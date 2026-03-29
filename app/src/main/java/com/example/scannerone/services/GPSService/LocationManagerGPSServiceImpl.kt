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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Implementazione di [GPSService] basata su [LocationManager] (AOSP).
 * NON dipende da Google Play Services.
 * Usa esclusivamente GPS_PROVIDER — se il GPS non è attivo, lancia eccezione.
 */
class LocationManagerGPSServiceImpl(private val context: Context) : GPSService {

    companion object {
        private const val TAG = "LocationManagerGPS"
        private const val TIMEOUT_MS = 15_000L
    }

    override suspend fun getPosition(): Position {
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

        Log.d(TAG, "Fix ottenuto: lat=${location.latitude}, lon=${location.longitude}, acc=${location.accuracy}")

        return Position(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy
        )
    }

    @Suppress("MissingPermission") // Il check è già stato fatto sopra
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
