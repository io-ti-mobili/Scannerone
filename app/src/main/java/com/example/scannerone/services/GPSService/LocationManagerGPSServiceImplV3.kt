package com.example.scannerone.services.GPSService

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
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
 * Implementazione V3 di [GPSService] — adatta il rate GPS al movimento reale.
 *
 * Estende V2 con un sistema a due velocità guidato da sensori hardware:
 *
 *  ┌─────────────────────────────────────────────────────────────────────┐
 *  │  MOVING  →  GPS a [HIGH_RATE_MS] (500ms)                           │
 *  │  STILL   →  GPS a [LOW_RATE_MS]  (3000ms) + TYPE_SIGNIFICANT_MOTION│
 *  └─────────────────────────────────────────────────────────────────────┘
 *
 * Transizioni di stato:
 *
 *   MOVING → STILL:
 *     Il GPS callback riceve fix con speed < [MOVING_SPEED_THRESHOLD] per
 *     [STILL_TIMEOUT_MS] consecutivi. Un Handler.postDelayed programma il
 *     passaggio a LOW_RATE. Se nel frattempo arriva un fix con speed alta,
 *     il pending callback viene cancellato e si rimane in MOVING.
 *
 *   STILL → MOVING:
 *     TYPE_SIGNIFICANT_MOTION triggerizza (implementato nel chip inerziale,
 *     consumo CPU = 0 in standby). Il GPS torna a HIGH_RATE immediatamente.
 *     TYPE_SIGNIFICANT_MOTION è one-shot → viene riarmato ogni volta che
 *     si entra in stato STILL.
 *
 * Risparmio energetico stimato:
 *   GPS a 500ms  ≈ 150-180mA  (chip GPS attivo ad alta frequenza)
 *   GPS a 3000ms ≈  80-100mA  (chip GPS in duty cycle ridotto)
 *   TYPE_SIGNIFICANT_MOTION ≈ < 1mA  (gira sul coprocessore inerziale)
 *
 * Compatibilità:
 *   TYPE_SIGNIFICANT_MOTION è garantito dall'Android CDD 4.3+ su tutti i
 *   dispositivi con accelerometro. Se non disponibile (emulatore, device
 *   molto vecchi), il service funziona normalmente in HIGH_RATE fisso.
 *
 * Thread model:
 *   - GPS callback → HandlerThread "gps-v3-thread"
 *   - Still timeout → Handler sullo stesso HandlerThread (non main thread)
 *   - TYPE_SIGNIFICANT_MOTION callback → thread sistema (poi rimbalzato su gps-v3-thread)
 *   - cachedPosition → @Volatile, accesso sicuro da qualsiasi thread
 */
class LocationManagerGPSServiceImplV3(private val context: Context) : GPSService {

    companion object {
        private const val TAG = "LocationManagerGPSv3"
        private const val TIMEOUT_MS = 15_000L

        /** Intervallo GPS quando il dispositivo è in movimento */
        private const val HIGH_RATE_MS = WarDrivingConfig.GPS_UPDATE_INTERVAL_MS  // 500ms

        /** Intervallo GPS quando il dispositivo è fermo */
        private const val LOW_RATE_MS = 3_000L

        /** Tempo senza movimento rilevato prima di passare a LOW_RATE */
        private const val STILL_TIMEOUT_MS = 15_000L

        /** Soglia velocità per considerare il dispositivo in movimento (m/s ≈ 1.8 km/h) */
        private const val MOVING_SPEED_THRESHOLD = 0.5f

        /**
         * Soglia di fallback quando speed non è disponibile dall'hardware.
         * Rumore tipico GPS da fermo: 0.5-2m tra fix consecutivi.
         */
        private const val MIN_DIST_FALLBACK_M = 2.5f

        /** Oltre questa soglia il cachedPosition è considerato stale */
        private const val POSITION_FRESHNESS_MS = 2_000L
    }

    // ── Stato GPS ─────────────────────────────────────────────────────────────

    @Volatile private var cachedPosition: Position? = null
    @Volatile private var isContinuousActive = false
    @Volatile private var currentRateMs = HIGH_RATE_MS
    @Volatile private var isHighRate = true

    private var continuousListener: LocationListener? = null
    private var onPositionUpdateCallback: ((Position) -> Unit)? = null

    // HandlerThread condiviso per GPS callback e still-timeout handler
    private var handlerThread: HandlerThread? = null
    private var gpsHandler: Handler? = null

    // ── Sensore movimento ─────────────────────────────────────────────────────

    private var sensorManager: SensorManager? = null
    private var significantMotionSensor: Sensor? = null
    private var motionTriggerListener: TriggerEventListener? = null

    // Runnable programmato da postDelayed: eseguito dopo STILL_TIMEOUT_MS senza movimento
    private val switchToLowRateRunnable = Runnable {
        Log.d(TAG, "⏱ Still timeout scaduto (${STILL_TIMEOUT_MS / 1000}s) → GPS LOW RATE (${LOW_RATE_MS}ms)")
        applyRate(LOW_RATE_MS)
        armSignificantMotion()
    }

    // ── startContinuousUpdates ────────────────────────────────────────────────

    @Suppress("MissingPermission")
    override fun startContinuousUpdates(onUpdate: ((Position) -> Unit)?) {
        if (isContinuousActive) return

        onPositionUpdateCallback = onUpdate
        val locationManager = requireLocationManager()
        checkFineLocationPermission()

        // Avvia HandlerThread unico per GPS callback e still-timeout
        val ht = HandlerThread("gps-v3-thread").also {
            it.start()
            handlerThread = it
        }
        gpsHandler = Handler(ht.looper)

        // Inizia sempre in HIGH_RATE — TYPE_SIGNIFICANT_MOTION si occupa
        // di segnalare il movimento, non di rilevare che siamo già fermi.
        // Dopo STILL_TIMEOUT_MS senza movimento il rate scenderà da solo.
        isHighRate = true
        currentRateMs = HIGH_RATE_MS

        // Prepara il SensorManager per TYPE_SIGNIFICANT_MOTION
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        significantMotionSensor = sensorManager
            ?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
            .also {
                if (it == null) Log.w(TAG, "TYPE_SIGNIFICANT_MOTION non disponibile — GPS rate fisso a ${HIGH_RATE_MS}ms")
                else Log.d(TAG, "TYPE_SIGNIFICANT_MOTION disponibile ✓")
            }

        registerLocationUpdates(locationManager, ht.looper, HIGH_RATE_MS)
        isContinuousActive = true

        // Avvia il countdown "sono fermo" già al boot:
        // se dopo 15s non ci sono stati fix di movimento, passa a LOW_RATE.
        scheduleSwitchToLowRate()

        Log.d(TAG, "Aggiornamenti continui avviati — rate iniziale: ${HIGH_RATE_MS}ms")
    }

    // ── stopContinuousUpdates ─────────────────────────────────────────────────

    override fun stopContinuousUpdates() {
        if (!isContinuousActive) return

        gpsHandler?.removeCallbacks(switchToLowRateRunnable)
        disarmSignificantMotion()

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        continuousListener?.let { locationManager?.removeUpdates(it) }

        handlerThread?.quitSafely()
        handlerThread = null
        gpsHandler = null

        continuousListener = null
        onPositionUpdateCallback = null
        isContinuousActive = false
        cachedPosition = null
        sensorManager = null
        significantMotionSensor = null

        Log.d(TAG, "Aggiornamenti continui fermati")
    }

    // ── getPosition ───────────────────────────────────────────────────────────

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

    // ── Gestione rate GPS ─────────────────────────────────────────────────────

    /**
     * Registra (o ri-registra) i location updates con il nuovo intervallo.
     * Chiamato sia all'avvio che ad ogni cambio di rate.
     * Deve girare sullo stesso thread che gestisce il LocationListener,
     * altrimenti Android può lanciare IllegalStateException.
     */
    @Suppress("MissingPermission")
    private fun registerLocationUpdates(
        locationManager: LocationManager,
        looper: Looper,
        intervalMs: Long
    ) {
        // Rimuovi listener esistente prima di re-registrare
        continuousListener?.let { locationManager.removeUpdates(it) }

        val listener = buildQualityAwareListener()
        var activeProviders = 0

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    intervalMs,
                    WarDrivingConfig.GPS_MIN_DISTANCE_M,
                    listener,
                    looper
                )
                activeProviders++
            }
        } catch (e: Exception) {
            Log.w(TAG, "Impossibile attivare GPS_PROVIDER", e)
        }

        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    intervalMs,
                    WarDrivingConfig.GPS_MIN_DISTANCE_M,
                    listener,
                    looper
                )
                activeProviders++
            }
        } catch (e: Exception) {
            Log.w(TAG, "Impossibile attivare NETWORK_PROVIDER", e)
        }

        if (activeProviders == 0 && !isContinuousActive) {
            // Chiamato solo durante startContinuousUpdates (isContinuousActive ancora false)
            handlerThread?.quitSafely()
            handlerThread = null
            throw Exception("Nessun provider disponibile. Attiva GPS o la rete.")
        }

        continuousListener = listener
        currentRateMs = intervalMs
    }

    /**
     * Passa al rate indicato ri-registrando i location updates.
     * Eseguito sempre sul gpsHandler thread per thread safety con LocationManager.
     */
    private fun applyRate(newRateMs: Long) {
        if (currentRateMs == newRateMs) return

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return
        val looper = handlerThread?.looper ?: return

        // re-registrazione deve avvenire sullo stesso looper dei callback
        gpsHandler?.post {
            registerLocationUpdates(locationManager, looper, newRateMs)
            isHighRate = newRateMs == HIGH_RATE_MS
            Log.d(TAG, "GPS rate → ${newRateMs}ms (${if (isHighRate) "HIGH" else "LOW"})")
        }
    }

    // ── Still timeout ─────────────────────────────────────────────────────────

    /** Schedula il passaggio a LOW_RATE dopo STILL_TIMEOUT_MS. */
    private fun scheduleSwitchToLowRate() {
        gpsHandler?.removeCallbacks(switchToLowRateRunnable)
        gpsHandler?.postDelayed(switchToLowRateRunnable, STILL_TIMEOUT_MS)
    }

    /** Annulla il countdown "sono fermo" — il dispositivo si sta muovendo. */
    private fun cancelSwitchToLowRate() {
        gpsHandler?.removeCallbacks(switchToLowRateRunnable)
    }

    // ── TYPE_SIGNIFICANT_MOTION ───────────────────────────────────────────────

    /**
     * Arma il trigger TYPE_SIGNIFICANT_MOTION.
     * È one-shot: si auto-disarma dopo il primo trigger.
     * Va riarmato ogni volta che entriamo in stato STILL.
     */
    private fun armSignificantMotion() {
        val sm = sensorManager ?: return
        val sensor = significantMotionSensor ?: return

        // Disarma eventuale listener precedente prima di riregistrare
        disarmSignificantMotion()

        val listener = object : TriggerEventListener() {
            override fun onTrigger(event: TriggerEvent) {
                // Questo callback arriva su un thread di sistema.
                // Rimbalziamo su gpsHandler per thread safety con LocationManager.
                Log.d(TAG, "🚶 TYPE_SIGNIFICANT_MOTION → dispositivo in movimento, GPS → HIGH RATE")
                gpsHandler?.post {
                    if (!isHighRate) {
                        applyRate(HIGH_RATE_MS)
                    }
                    // Riavvia il countdown: se non rileviamo movimento GPS entro
                    // STILL_TIMEOUT_MS torneremo a LOW_RATE e riarmeremo.
                    scheduleSwitchToLowRate()
                }
                // NON riarmarlo qui: aspettiamo conferma dal GPS speed.
                // Il riarmo avviene in switchToLowRateRunnable quando il GPS
                // conferma che siamo di nuovo fermi.
            }
        }

        sm.requestTriggerSensor(listener, sensor)
        motionTriggerListener = listener
        Log.d(TAG, "TYPE_SIGNIFICANT_MOTION armato — in attesa di movimento hardware")
    }

    private fun disarmSignificantMotion() {
        val sm = sensorManager ?: return
        motionTriggerListener?.let {
            sm.cancelTriggerSensor(it, significantMotionSensor)
            motionTriggerListener = null
        }
    }

    // ── Listener GPS con selezione qualità e rilevamento movimento ────────────

    private fun buildQualityAwareListener() = object : LocationListener {

        // Ultimo fix valido per calcolo distanza (fallback senza speed hardware)
        private var prevPosition: Position? = null

        override fun onLocationChanged(location: Location) {
            val newPosition = location.toPosition()

            // ── Aggiornamento cachedPosition (V2 quality logic) ──────────────
            val current = cachedPosition
            val currentAge = current?.let { System.currentTimeMillis() - it.timestamp }
                ?: Long.MAX_VALUE

            val shouldUpdateCache = current == null
                    || currentAge > POSITION_FRESHNESS_MS
                    || newPosition.accuracy < current.accuracy

            if (shouldUpdateCache) {
                cachedPosition = newPosition
            }

            // ── Rilevamento movimento per rate adattivo ───────────────────────
            //
            // Usiamo prima la velocità hardware (più affidabile).
            // Fallback: distanza dal fix precedente (stesso approccio di WarDrivingServiceImplWiggle).
            //
            // Se il dispositivo si muove:
            //   → cancella il countdown "sono fermo"
            //   → se eravamo in LOW_RATE, torna a HIGH_RATE
            //
            // Se il dispositivo è fermo:
            //   → (ri)schedula il countdown "sono fermo"
            //   → alla scadenza passerà a LOW_RATE e armerà TYPE_SIGNIFICANT_MOTION

            val isMoving = if (newPosition.hasSpeed) {
                newPosition.speed > MOVING_SPEED_THRESHOLD
            } else {
                val prev = prevPosition
                if (prev != null) prev.distanceTo(newPosition) > MIN_DIST_FALLBACK_M
                else false
            }

            if (isMoving) {
                cancelSwitchToLowRate()
                if (!isHighRate) {
                    Log.d(TAG, "📍 Movimento rilevato da GPS speed (${newPosition.speed}m/s) → HIGH RATE")
                    applyRate(HIGH_RATE_MS)
                }
                scheduleSwitchToLowRate()  // reset del countdown ad ogni fix di movimento
            }
            // Se !isMoving non facciamo nulla: il countdown era già schedulato,
            // lasceremo che scada naturalmente.

            prevPosition = newPosition

            // Callback al WarDrivingService (riceve sempre tutti i fix)
            onPositionUpdateCallback?.invoke(newPosition)
        }

        @Deprecated("Deprecated in API level 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

        override fun onProviderEnabled(provider: String) {
            Log.d(TAG, "Provider abilitato: $provider")
        }

        override fun onProviderDisabled(provider: String) {
            Log.w(TAG, "Provider disabilitato: $provider")
        }
    }

    // ── Single update ─────────────────────────────────────────────────────────

    @Suppress("MissingPermission")
    private suspend fun singleUpdate(): Position {
        val locationManager = requireLocationManager()

        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val netEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!gpsEnabled && !netEnabled) {
            throw Exception("Tutti i provider disabilitati. Attiva GPS o rete dalle impostazioni.")
        }

        checkFineLocationPermission()

        val tempThread = HandlerThread("gps-v3-single-thread").also { it.start() }
        return try {
            val location = withTimeoutOrNull(TIMEOUT_MS) {
                requestSingleUpdate(locationManager, tempThread.looper)
            } ?: throw Exception("Timeout: nessun fix in ${TIMEOUT_MS / 1000}s")

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

    // ── Helpers ───────────────────────────────────────────────────────────────

    override fun isGpsEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun requireLocationManager(): LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: throw Exception("LocationManager non disponibile")

    private fun checkFineLocationPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) throw Exception("Permesso ACCESS_FINE_LOCATION non concesso.")
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