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
import com.example.scannerone.services.motion.MotionConfig
import com.example.scannerone.services.motion.MotionState
import com.example.scannerone.services.motion.MotionStateResolver
import com.example.scannerone.services.WarDrivingService.WarDrivingConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class LocationManagerGPSServiceImplV4(private val context: Context) : GPSService {
    companion object {
        private const val TAG = "LocationManagerGPSv3"
        private const val TIMEOUT_MS = 15_000L
        private const val POSITION_FRESHNESS_MS = 2_000L
    }

    @Volatile private var cachedPosition: Position? = null
    @Volatile private var isContinuousActive = false
    @Volatile private var currentRateMs = MotionConfig.PROFILE_WALKING.gpsRateMs
    @Volatile private var isHighRate = true

    private var continuousListener: LocationListener? = null
    private var onPositionUpdateCallback: ((Position) -> Unit)? = null
    private var handlerThread: HandlerThread? = null
    private var gpsHandler: Handler? = null
    private var sensorManager: SensorManager? = null
    private var significantMotionSensor: Sensor? = null
    private var motionTriggerListener: TriggerEventListener? = null

    private val switchToLowRateRunnable = Runnable {
        Log.d(TAG, "Still timeout → GPS STILL RATE (${MotionConfig.PROFILE_STILL.gpsRateMs}ms)")
        applyRate(MotionConfig.PROFILE_STILL.gpsRateMs)
        armSignificantMotion()
    }

    @Suppress("MissingPermission")
    override fun startContinuousUpdates(onUpdate: ((Position) -> Unit)?) {
        if (isContinuousActive) return
        onPositionUpdateCallback = onUpdate
        val locationManager = requireLocationManager()
        checkFineLocationPermission()

        val ht = HandlerThread("gps-v3-thread").also { it.start(); handlerThread = it }
        gpsHandler = Handler(ht.looper)
        currentRateMs = MotionConfig.PROFILE_WALKING.gpsRateMs
        isHighRate = true

        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        significantMotionSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION).also {
            if (it == null) Log.w(TAG, "TYPE_SIGNIFICANT_MOTION non disponibile")
            else Log.d(TAG, "TYPE_SIGNIFICANT_MOTION disponibile")
        }

        registerLocationUpdates(locationManager, ht.looper, currentRateMs)
        isContinuousActive = true
        scheduleSwitchToLowRate(MotionConfig.PROFILE_WALKING.stillTimeoutMs)
        Log.d(TAG, "Avviato — rate: ${currentRateMs}ms")
    }

    override fun stopContinuousUpdates() {
        if (!isContinuousActive) return
        gpsHandler?.removeCallbacks(switchToLowRateRunnable)
        disarmSignificantMotion()
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        continuousListener?.let { lm?.removeUpdates(it) }
        handlerThread?.quitSafely()
        handlerThread = null; gpsHandler = null; continuousListener = null
        onPositionUpdateCallback = null; isContinuousActive = false
        cachedPosition = null; sensorManager = null; significantMotionSensor = null
        Log.d(TAG, "Fermato")
    }

    override suspend fun getPosition(): Position {
        if (isContinuousActive) { cachedPosition?.let { return it }; return waitForCachedPosition() }
        return singleUpdate()
    }

    private suspend fun waitForCachedPosition(): Position {
        while (true) { cachedPosition?.let { return it }; delay(100L) }
    }

    @Suppress("MissingPermission")
    private fun registerLocationUpdates(locationManager: LocationManager, looper: Looper, intervalMs: Long) {
        continuousListener?.let { locationManager.removeUpdates(it) }
        val listener = buildQualityAwareListener()
        var active = 0
        try { if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, intervalMs, WarDrivingConfig.GPS_MIN_DISTANCE_M, listener, looper); active++ }
        } catch (e: Exception) { Log.w(TAG, "GPS_PROVIDER non disponibile", e) }
        try { if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, intervalMs, WarDrivingConfig.GPS_MIN_DISTANCE_M, listener, looper); active++ }
        } catch (e: Exception) { Log.w(TAG, "NETWORK_PROVIDER non disponibile", e) }
        if (active == 0 && !isContinuousActive) { handlerThread?.quitSafely(); handlerThread = null; throw Exception("Nessun provider disponibile.") }
        continuousListener = listener; currentRateMs = intervalMs
    }

    private fun applyRate(newRateMs: Long) {
        if (currentRateMs == newRateMs) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val looper = handlerThread?.looper ?: return
        gpsHandler?.post { registerLocationUpdates(lm, looper, newRateMs); isHighRate = newRateMs != MotionConfig.PROFILE_STILL.gpsRateMs; Log.d(TAG, "GPS rate → ${newRateMs}ms") }
    }

    private fun scheduleSwitchToLowRate(delayMs: Long) { gpsHandler?.removeCallbacks(switchToLowRateRunnable); gpsHandler?.postDelayed(switchToLowRateRunnable, delayMs) }
    private fun cancelSwitchToLowRate() { gpsHandler?.removeCallbacks(switchToLowRateRunnable) }

    private fun armSignificantMotion() {
        val sm = sensorManager ?: return; val sensor = significantMotionSensor ?: return
        disarmSignificantMotion()
        val listener = object : TriggerEventListener() {
            override fun onTrigger(event: TriggerEvent) {
                Log.d(TAG, "TYPE_SIGNIFICANT_MOTION → HIGH RATE")
                gpsHandler?.post { if (!isHighRate) applyRate(MotionConfig.PROFILE_WALKING.gpsRateMs); scheduleSwitchToLowRate(MotionConfig.PROFILE_WALKING.stillTimeoutMs) }
            }
        }
        sm.requestTriggerSensor(listener, sensor); motionTriggerListener = listener
        Log.d(TAG, "TYPE_SIGNIFICANT_MOTION armato")
    }

    private fun disarmSignificantMotion() { motionTriggerListener?.let { sensorManager?.cancelTriggerSensor(it, significantMotionSensor); motionTriggerListener = null } }

    private fun buildQualityAwareListener() = object : LocationListener {
        private var prevPosition: Position? = null

        override fun onLocationChanged(location: Location) {
            val newPos = location.toPosition()
            val current = cachedPosition
            val age = current?.let { System.currentTimeMillis() - it.timestamp } ?: Long.MAX_VALUE
            if (current == null || age > POSITION_FRESHNESS_MS || newPos.accuracy < current.accuracy) cachedPosition = newPos

            val state = MotionStateResolver.resolve(newPos, prevPosition)
            val profile = MotionConfig.profileFor(state)
            when (state) {
                is MotionState.Still -> { /* countdown scade naturalmente */ }
                is MotionState.Walking, is MotionState.InVehicle -> {
                    cancelSwitchToLowRate()
                    if (!isHighRate) { Log.d(TAG, "$state → HIGH RATE (${profile.gpsRateMs}ms)"); applyRate(profile.gpsRateMs) }
                    scheduleSwitchToLowRate(profile.stillTimeoutMs)
                }
            }
            prevPosition = newPos
            onPositionUpdateCallback?.invoke(newPos)
        }

        @Deprecated("Deprecated in API level 29") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) { Log.d(TAG, "Provider abilitato: $provider") }
        override fun onProviderDisabled(provider: String) { Log.w(TAG, "Provider disabilitato: $provider") }
    }

    @Suppress("MissingPermission")
    private suspend fun singleUpdate(): Position {
        val lm = requireLocationManager()
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER) && !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) throw Exception("Provider disabilitati.")
        checkFineLocationPermission()
        val tempThread = HandlerThread("gps-v3-single").also { it.start() }
        return try {
            val loc = withTimeoutOrNull(TIMEOUT_MS) { requestSingleUpdate(lm, tempThread.looper) } ?: throw Exception("Timeout: nessun fix in ${TIMEOUT_MS / 1000}s")
            loc.toPosition()
        } finally { tempThread.quitSafely() }
    }

    @Suppress("MissingPermission")
    private suspend fun requestSingleUpdate(lm: LocationManager, looper: Looper): Location = suspendCancellableCoroutine { cont ->
        val resumed = AtomicBoolean(false)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) { if (resumed.compareAndSet(false, true)) { lm.removeUpdates(this); cont.resume(location) } }
            @Deprecated("Deprecated in API level 29") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        try { if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, looper) } catch (e: Exception) { Log.w(TAG, "GPS single update non disponibile", e) }
        try { if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, looper) } catch (e: Exception) { Log.w(TAG, "NETWORK single update non disponibile", e) }
        cont.invokeOnCancellation { lm.removeUpdates(listener) }
    }

    override fun isGpsEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun requireLocationManager() = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: throw Exception("LocationManager non disponibile")
    private fun checkFineLocationPermission() { if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) throw Exception("Permesso ACCESS_FINE_LOCATION non concesso.") }
    private fun Location.toPosition() = Position(latitude, longitude, accuracy, if (hasSpeed()) speed else 0f, hasSpeed(), System.currentTimeMillis())
}