package com.example.scannerone.services.GPSService

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.scannerone.services.WarDrivingService.WarDrivingConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Implementazione V2 di [GPSService] basata su [LocationManager] (AOSP, no Google Play Services).
 *
 * Miglioramenti rispetto a V1:
 *
 * 1. [HandlerThread] dedicato per i callback GPS.
 *    V1 usava Looper.getMainLooper(): ogni fix GPS bloccava il main thread.
 *    Con un HandlerThread separato i callback girano in background senza
 *    interferire con la UI.
 *
 * 2. Selezione qualità tra GPS_PROVIDER e NETWORK_PROVIDER.
 *    V1 usava lo stesso listener per entrambi i provider: il NETWORK_PROVIDER
 *    poteva sovrascrivere cachedPosition con accuracy 80m anche quando GPS
 *    aveva appena fornito un fix a 5m.
 *    V2 aggiorna cachedPosition solo se il nuovo fix è più accurato,
 *    oppure se quello corrente è stale (> POSITION_FRESHNESS_MS).
 *    Il callback esterno riceve comunque TUTTI i fix (la logica di filtraggio
 *    appartiene al chiamante, es. WarDrivingServiceImplWiggle).
 *
 * 3. [singleUpdate] usa HandlerThread anche per il caso non-continuo,
 *    evitando di occupare il main looper durante un requestSingleUpdate.
 */
class LocationManagerGPSServiceImplV2(private val context: Context) : GPSService {

    companion object {
        private const val TAG = "LocationManagerGPSv2"
        private const val TIMEOUT_MS = 15_000L

        /**
         * Oltre questa soglia il cachedPosition viene considerato stale e
         * qualsiasi nuovo fix viene accettato, anche se meno accurato.
         * Con GPS_UPDATE_INTERVAL_MS = 500ms, in condizioni normali il fix
         * non dovrebbe mai avere più di ~1s. Se supera 2s, c'è un problema.
         */
        private const val POSITION_FRESHNESS_MS = 2_000L
    }

    @Volatile private var cachedPosition: Position? = null
    @Volatile private var isContinuousActive = false

    private var continuousListener: LocationListener? = null
    private var onPositionUpdateCallback: ((Position) -> Unit)? = null

    // HandlerThread per i callback GPS (background, non main thread)
    private var handlerThread: HandlerThread? = null

    // ── Avvio aggiornamenti continui ────────────────────────────────────────

    @Suppress("MissingPermission")
    override fun startContinuousUpdates(onUpdate: ((Position) -> Unit)?) {
        if (isContinuousActive) return

        onPositionUpdateCallback = onUpdate

        val locationManager = requireLocationManager()
        checkFineLocationPermission()

        val ht = HandlerThread("gps-updates-thread").also {
            it.start()
            handlerThread = it
        }

        val listener = buildQualityAwareListener()
        var activeProviders = 0

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    WarDrivingConfig.GPS_UPDATE_INTERVAL_MS,
                    WarDrivingConfig.GPS_MIN_DISTANCE_M,
                    listener,
                    ht.looper
                )
                activeProviders++
                Log.d(TAG, "GPS_PROVIDER attivato (${WarDrivingConfig.GPS_UPDATE_INTERVAL_MS}ms interval)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Impossibile attivare GPS_PROVIDER", e)
        }

        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    WarDrivingConfig.GPS_UPDATE_INTERVAL_MS,
                    WarDrivingConfig.GPS_MIN_DISTANCE_M,
                    listener,
                    ht.looper
                )
                activeProviders++
                Log.d(TAG, "NETWORK_PROVIDER attivato (fallback)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Impossibile attivare NETWORK_PROVIDER", e)
        }

        if (activeProviders == 0) {
            ht.quitSafely()
            handlerThread = null
            throw Exception("Nessun provider disponibile. Attiva GPS o la rete.")
        }

        continuousListener = listener
        isContinuousActive = true
        Log.d(TAG, "Aggiornamenti continui avviati ($activeProviders provider attivi)")
    }

    // ── Stop ────────────────────────────────────────────────────────────────

    override fun stopContinuousUpdates() {
        if (!isContinuousActive) return

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        continuousListener?.let { locationManager?.removeUpdates(it) }

        handlerThread?.quitSafely()
        handlerThread = null

        continuousListener = null
        onPositionUpdateCallback = null
        isContinuousActive = false
        cachedPosition = null

        Log.d(TAG, "Aggiornamenti continui fermati")
    }

    // ── getPosition ──────────────────────────────────────────────────────────

    override suspend fun getPosition(): Position {
        if (isContinuousActive) {
            cachedPosition?.let { return it }
            Log.d(TAG, "Modalità continua attiva, attesa primo fix...")
            return waitForCachedPosition()
        }
        return singleUpdate()
    }

    private suspend fun waitForCachedPosition(): Position {
        while (true) {
            cachedPosition?.let { return it }
            delay(100L)
        }
    }

    // ── Listener con selezione qualità ───────────────────────────────────────

    /**
     * Costruisce un listener che:
     * - Aggiorna cachedPosition solo se il nuovo fix è migliore o quello corrente è stale
     * - Invoca sempre il callback esterno (la logica di filtraggio è del chiamante)
     */
    private fun buildQualityAwareListener() = object : LocationListener {

        override fun onLocationChanged(location: Location) {
            val newPosition = location.toPosition()

            val current = cachedPosition
            val currentAge = current?.let { System.currentTimeMillis() - it.timestamp } ?: Long.MAX_VALUE

            val shouldUpdate = current == null
                    || currentAge > POSITION_FRESHNESS_MS       // fix corrente stale → accetta comunque
                    || newPosition.accuracy < current.accuracy   // nuovo fix più preciso

            if (shouldUpdate) {
                cachedPosition = newPosition
                Log.d(
                    TAG,
                    "cachedPosition aggiornato: acc=${newPosition.accuracy}m " +
                            "provider=${location.provider} " +
                            if (current != null) "(era ${current.accuracy}m, age=${currentAge}ms)" else "(primo fix)"
                )
            } else {
                Log.v(
                    TAG,
                    "Fix scartato (${location.provider}): acc=${newPosition.accuracy}m > " +
                            "cached=${current?.accuracy}m age=${currentAge}ms"
                )
            }

            // Il callback esterno riceve sempre tutti i fix indipendentemente dalla qualità:
            // la logica di filtraggio appartiene al WarDrivingService.
            onPositionUpdateCallback?.invoke(newPosition)
        }

        @Deprecated("Deprecated in API level 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

        override fun onProviderEnabled(provider: String) {
            Log.d(TAG, "Provider abilitato: $provider")
        }

        override fun onProviderDisabled(provider: String) {
            Log.w(TAG, "Provider disabilitato durante aggiornamenti continui: $provider")
        }
    }

    // ── Single update (per getPosition() fuori modalità continua) ───────────

    @Suppress("MissingPermission")
    private suspend fun singleUpdate(): Position {
        val locationManager = requireLocationManager()

        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val netEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!gpsEnabled && !netEnabled) {
            throw Exception("Tutti i provider disabilitati. Attiva GPS o rete dalle impostazioni.")
        }

        checkFineLocationPermission()

        // Usiamo un HandlerThread temporaneo anche per il single update,
        // così non occupiamo il main looper durante l'attesa.
        val tempThread = HandlerThread("gps-single-update-thread").also { it.start() }

        return try {
            val location = withTimeoutOrNull(TIMEOUT_MS) {
                requestSingleUpdate(locationManager, tempThread.looper)
            } ?: throw Exception("Timeout: nessun fix in ${TIMEOUT_MS / 1000}s")

            Log.d(TAG, "Fix singolo: lat=${location.latitude}, lon=${location.longitude}, acc=${location.accuracy}m")
            location.toPosition()
        } finally {
            tempThread.quitSafely()
        }
    }

    @Suppress("MissingPermission")
    private suspend fun requestSingleUpdate(
        locationManager: LocationManager,
        looper: Looper
    ): Location = suspendCancellableCoroutine { continuation ->
        // AtomicBoolean garantisce che la continuation venga resumata una sola volta
        // anche se GPS e NETWORK rispondono quasi contemporaneamente.
        val resumed = AtomicBoolean(false)

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (resumed.compareAndSet(false, true)) {
                    locationManager.removeUpdates(this)
                    continuation.resume(location)
                }
            }

            @Deprecated("Deprecated in API level 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, looper)
            }
        } catch (e: Exception) {
            Log.w(TAG, "GPS_PROVIDER single update non disponibile", e)
        }

        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, looper)
            }
        } catch (e: Exception) {
            Log.w(TAG, "NETWORK_PROVIDER single update non disponibile", e)
        }

        continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    override fun isGpsEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun requireLocationManager(): LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: throw Exception("LocationManager non disponibile sul dispositivo")

    private fun checkFineLocationPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            throw Exception("Permesso ACCESS_FINE_LOCATION non concesso.")
        }
    }

    private fun Location.toPosition() = Position(
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        speed = if (hasSpeed()) speed else 0f,
        hasSpeed = hasSpeed(),
        timestamp = System.currentTimeMillis()
    )
}