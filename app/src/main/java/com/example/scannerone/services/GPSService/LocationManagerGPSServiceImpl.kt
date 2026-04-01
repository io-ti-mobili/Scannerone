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
    fun startContinuousUpdates(onUpdate: ((Position) -> Unit)? = null) {
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

        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            WarDrivingConfig.GPS_UPDATE_INTERVAL_MS,
            WarDrivingConfig.GPS_MIN_DISTANCE_M,
            listener,
            Looper.getMainLooper()
        )

        continuousListener = listener
        isContinuousActive = true
        Log.d(TAG, "Aggiornamenti GPS continui avviati")
    }

    fun stopContinuousUpdates() {
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
            val position = withTimeoutOrNull(TIMEOUT_MS) {
                waitForFirstCachedPosition()
            } ?: throw Exception("Timeout: impossibile ottenere un fix GPS in ${TIMEOUT_MS / 1000} secondi.")

            return position
        }

        return singleUpdate()
    }

    private suspend fun waitForFirstCachedPosition(): Position {
        return suspendCancellableCoroutine { continuation ->
            val checkInterval = 100L
            val thread = Thread {
                while (continuation.isActive) {
                    cachedPosition?.let {
                        if (continuation.isActive) {
                            continuation.resume(it)
                        }
                        return@Thread
                    }
                    Thread.sleep(checkInterval)
                }
            }
            thread.start()
            continuation.invokeOnCancellation { thread.interrupt() }
        }
    }

    private suspend fun singleUpdate(): Position {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: throw Exception("LocationManager non disponibile sul dispositivo")

        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            throw Exception("Il GPS è disattivato. Attivalo e riprova.")
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            throw Exception("Permesso ACCESS_FINE_LOCATION non concesso.")
        }

        val location = withTimeoutOrNull(TIMEOUT_MS) {
            requestSingleGPSUpdate(locationManager)
        } ?: throw Exception("Timeout: impossibile ottenere un fix GPS in ${TIMEOUT_MS / 1000} secondi.")

        Log.d(TAG, "Fix ottenuto: lat=${location.latitude}, lon=${location.longitude}, acc=${location.accuracy}m")

        return Position(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            timestamp = System.currentTimeMillis()
        )
    }

    @Suppress("MissingPermission")
    private suspend fun requestSingleGPSUpdate(locationManager: LocationManager): Location {
        return suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }

                @Deprecated("Deprecated in API level 29")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {
                    locationManager.removeUpdates(this)
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            Exception("GPS disattivato durante l'attesa del fix.")
                        )
                    }
                }
            }

            locationManager.requestSingleUpdate(
                LocationManager.GPS_PROVIDER,
                listener,
                Looper.getMainLooper()
            )

            continuation.invokeOnCancellation {
                locationManager.removeUpdates(listener)
            }
        }
    }
}
