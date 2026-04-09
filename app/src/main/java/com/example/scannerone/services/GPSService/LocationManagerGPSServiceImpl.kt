package com.example.scannerone.services.GPSService

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.scannerone.services.WarDrivingService.WarDrivingConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Implementazione di [GPSService] basata su [LocationManager] (AOSP).
 * NON dipende da Google Play Services. Usa esclusivamente GPS_PROVIDER.
 */
class LocationManagerGPSServiceImpl(private val context: Context) : GPSService {

    companion object {
        private const val TAG = "LocationManagerGPS"
        private const val TIMEOUT_MS = 15_000L
    }

    @Volatile
    private var cachedPosition: Position? = null

    @Volatile
    private var isContinuousActive = false

    private var continuousListener: LocationListener? = null
    private var onPositionUpdateCallback: ((Position) -> Unit)? = null

    @Suppress("MissingPermission")
    override fun startContinuousUpdates(onUpdate: ((Position) -> Unit)?) {
        if (isContinuousActive) return
        
        onPositionUpdateCallback = onUpdate

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: throw Exception("LocationManager non disponibile sul dispositivo")

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            throw Exception("Permesso ACCESS_FINE_LOCATION non concesso.")
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val position = Position(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    speed = if (location.hasSpeed()) location.speed else 0f,
                    hasSpeed = location.hasSpeed(),
                    timestamp = System.currentTimeMillis()
                )
                cachedPosition = position
                onPositionUpdateCallback?.invoke(position)
                Log.d(TAG, "Posizione aggiornata: lat=${location.latitude}, lon=${location.longitude}, acc=${location.accuracy}m")
            }

            @Deprecated("Deprecated in API level 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {
                Log.w(TAG, "GPS disattivato durante gli aggiornamenti continui")
            }
        }

        var providerAttivati = 0

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    WarDrivingConfig.GPS_UPDATE_INTERVAL_MS,
                    WarDrivingConfig.GPS_MIN_DISTANCE_M,
                    listener,
                    Looper.getMainLooper()
                )
                providerAttivati++
            }
        } catch (e: Exception) { Log.w(TAG, "Impossibile attivare GPS_PROVIDER", e) }

        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    WarDrivingConfig.GPS_UPDATE_INTERVAL_MS,
                    WarDrivingConfig.GPS_MIN_DISTANCE_M,
                    listener,
                    Looper.getMainLooper()
                )
                providerAttivati++
            }
        } catch (e: Exception) { Log.w(TAG, "Impossibile attivare NETWORK_PROVIDER", e) }

        if (providerAttivati == 0) {
            throw Exception("Nessun provider di geolocalizzazione disponibile o abilitato. Attiva il GPS/Rete.")
        }

        continuousListener = listener
        isContinuousActive = true
        Log.d(TAG, "Aggiornamenti continui avviati ($providerAttivati provider attivi)")
    }

    override fun stopContinuousUpdates() {
        if (!isContinuousActive) return

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        continuousListener?.let { locationManager?.removeUpdates(it) }
        continuousListener = null
        isContinuousActive = false
        cachedPosition = null
        onPositionUpdateCallback = null
        Log.d(TAG, "Aggiornamenti GPS continui fermati")
    }

    override suspend fun getPosition(): Position {
        if (isContinuousActive) {
            cachedPosition?.let { return it }

            Log.d(TAG, "Modalità continua attiva, in attesa del primo fix...")
            return waitForFirstCachedPosition()
        }

        return singleUpdate()
    }

    private suspend fun waitForFirstCachedPosition(): Position {
        val checkInterval = 100L
        while (true) {
            cachedPosition?.let { return it }
            kotlinx.coroutines.delay(checkInterval)
        }
    }

    private suspend fun singleUpdate(): Position {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: throw Exception("LocationManager non disponibile sul dispositivo")

        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!gpsEnabled && !networkEnabled) {
            throw Exception("Tutti i provider di geolocalizzazione sono disabilitati. Attivali dalle impostazioni.")
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            throw Exception("Permesso ACCESS_FINE_LOCATION non concesso.")
        }

        val location = withTimeoutOrNull(TIMEOUT_MS) {
            requestSingleUpdate(locationManager)
        } ?: throw Exception("Timeout: impossibile ottenere un fix GPS in ${TIMEOUT_MS / 1000} secondi.")

        Log.d(TAG, "Fix ottenuto: lat=${location.latitude}, lon=${location.longitude}, acc=${location.accuracy}m")

        return Position(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            speed = if (location.hasSpeed()) location.speed else 0f,
            hasSpeed = location.hasSpeed(),
            timestamp = System.currentTimeMillis()
        )
    }

    @Suppress("MissingPermission")
    private suspend fun requestSingleUpdate(locationManager: LocationManager): Location {
        return suspendCancellableCoroutine { continuation ->
            // AtomicBoolean garantisce che la continuation venga resumata una sola volta,
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
                    locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, Looper.getMainLooper())
                }
            } catch (e: Exception) { Log.w(TAG, "Impossibile registrare GPS_PROVIDER per single update", e) }

            try {
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, Looper.getMainLooper())
                }
            } catch (e: Exception) { Log.w(TAG, "Impossibile registrare NETWORK_PROVIDER per single update", e) }

            continuation.invokeOnCancellation {
                locationManager.removeUpdates(listener)
            }
        }
    }

    override fun isGpsEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || 
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}
