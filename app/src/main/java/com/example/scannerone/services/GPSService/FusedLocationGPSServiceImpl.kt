package com.example.scannerone.services.GPSService

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import com.example.scannerone.services.WarDrivingService.WarDrivingConfig
import com.example.scannerone.services.motion.FusedMotionStateSource
import com.example.scannerone.services.motion.MotionConfig
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Implementazione di [GPSService] basata su **Fused Location Provider** di Google.
 *
 * Rispetto a [LocationManagerGPSServiceImplV4]:
 * - Nessun [android.os.HandlerThread] o [android.os.Looper] da gestire manualmente
 * - Nessuna API deprecated ([android.location.LocationManager.requestSingleUpdate])
 * - Adattamento del rate GPS guidato da [FusedMotionStateSource.state] (StateFlow)
 * - Nessun [android.hardware.Sensor.TYPE_SIGNIFICANT_MOTION]: la Transition API (già
 *   integrata in [com.example.scannerone.services.motion.ActivityRecognitionSource])
 *   svolge lo stesso ruolo in modo più affidabile
 * - FLP fonde automaticamente GPS + network + WiFi positioning → migliore accuracy
 *   in ambienti urbani e indoor
 */
class FusedLocationGPSServiceImpl(
    private val context: Context,
    private val fusedMotionSource: FusedMotionStateSource
) : GPSService {

    companion object {
        private const val TAG = "FusedLocationGPS"
        private const val POSITION_FRESHNESS_MS = 2_000L
        private const val TIMEOUT_MS = 15_000L
    }

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    @Volatile private var cachedPosition: Position? = null
    @Volatile private var isContinuousActive = false
    @Volatile private var currentIntervalMs = -1L   // -1 = non avviato
    @Volatile private var prevPosition: Position? = null

    private var locationCallback: LocationCallback? = null
    private var onPositionUpdateCallback: ((Position) -> Unit)? = null

    // Scope per osservare FusedMotionStateSource e adattare il rate
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    override fun startContinuousUpdates(onUpdate: ((Position) -> Unit)?) {
        if (isContinuousActive) return
        onPositionUpdateCallback = onUpdate

        val callback = buildCallback()
        locationCallback = callback
        isContinuousActive = true

        // Avvia con profilo Walking (alta frequenza)
        applyRate(MotionConfig.PROFILE_WALKING.gpsRateMs)

        // Adatta il rate GPS ai cambiamenti di stato di movimento
        // drop(1): salta lo stato iniziale Still per non rallentare subito il GPS all'avvio
        scope.launch {
            fusedMotionSource.state
                .drop(1)
                .collect { state ->
                    if (!isContinuousActive) return@collect
                    val profile = MotionConfig.profileFor(state)
                    Log.d(TAG, "Stato → $state: rate ${profile.gpsRateMs}ms")
                    applyRate(profile.gpsRateMs)
                }
        }

        Log.d(TAG, "Avviato — rate: ${currentIntervalMs}ms")
    }

    override fun stopContinuousUpdates() {
        if (!isContinuousActive) return
        isContinuousActive = false
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null
        onPositionUpdateCallback = null
        cachedPosition = null
        prevPosition = null
        currentIntervalMs = -1L
        scope.coroutineContext.cancelChildren()
        Log.d(TAG, "Fermato")
    }

    // ── Posizione singola ─────────────────────────────────────────────────

    override suspend fun getPosition(): Position {
        if (isContinuousActive) {
            cachedPosition?.let { return it }
            return waitForCachedPosition()
        }
        return singleUpdate()
    }

    private suspend fun waitForCachedPosition(): Position {
        while (true) { cachedPosition?.let { return it }; delay(100L) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun singleUpdate(): Position {
        val cts = CancellationTokenSource()
        return withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { loc ->
                        if (!cont.isCompleted) {
                            if (loc != null) cont.resume(loc.toPosition())
                            else cont.resumeWithException(Exception("FLP ha restituito posizione null"))
                        }
                    }
                    .addOnFailureListener { e ->
                        if (!cont.isCompleted) cont.resumeWithException(e)
                    }
                cont.invokeOnCancellation { cts.cancel() }
            }
        } ?: throw Exception("Timeout: nessun fix in ${TIMEOUT_MS / 1000}s")
    }

    // ── Rate adaptation ───────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun applyRate(newIntervalMs: Long) {
        if (newIntervalMs == currentIntervalMs) return
        val callback = locationCallback ?: return

        val priority = if (newIntervalMs <= MotionConfig.PROFILE_WALKING.gpsRateMs)
            Priority.PRIORITY_HIGH_ACCURACY
        else
            Priority.PRIORITY_BALANCED_POWER_ACCURACY

        val request = LocationRequest.Builder(priority, newIntervalMs)
            .setMinUpdateIntervalMillis(newIntervalMs / 2)
            .setMinUpdateDistanceMeters(WarDrivingConfig.GPS_MIN_DISTANCE_M)
            .build()

        // FLP accetta requestLocationUpdates ripetute per aggiornare il rate senza
        // deregistrare il callback — non è necessario rimuovere e riaggiungere
        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        currentIntervalMs = newIntervalMs
        Log.d(TAG, "GPS rate → ${newIntervalMs}ms (priority=$priority)")
    }

    // ── Callback ──────────────────────────────────────────────────────────

    private fun buildCallback() = object : LocationCallback() {

        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val newPos = location.toPosition()

            // Filtro Null Island
            if (newPos.latitude == 0.0 && newPos.longitude == 0.0) {
                Log.w(TAG, "[GLITCH] Null Island ignorato")
                return
            }

            // Cache quality-aware: aggiorna solo se il fix è più fresco o più preciso
            val current = cachedPosition
            val age = current?.let { System.currentTimeMillis() - it.timestamp } ?: Long.MAX_VALUE
            if (current == null || age > POSITION_FRESHNESS_MS || newPos.accuracy < current.accuracy) {
                cachedPosition = newPos
            }

            // Aggiorna fallback GPS in FusedMotionStateSource (usato solo se AR non disponibile)
            fusedMotionSource.updateFromGps(newPos, prevPosition)

            prevPosition = newPos
            onPositionUpdateCallback?.invoke(newPos)
        }

        override fun onLocationAvailability(availability: LocationAvailability) {
            if (!availability.isLocationAvailable) {
                Log.w(TAG, "Posizione non disponibile (GPS perso o provider disabilitati)")
            }
        }
    }

    // ── Stato provider ────────────────────────────────────────────────────

    override fun isGpsEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun Location.toPosition() = Position(
        latitude, longitude, accuracy,
        if (hasSpeed()) speed else 0f,
        hasSpeed(),
        System.currentTimeMillis()
    )
}
