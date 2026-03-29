package com.example.scannerone.services.nominatimApi

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Proxy rate-limited per [NominatimApi] (Proxy Design Pattern).
 *
 * Nominatim ha un rate limit ufficiale di 1 richiesta al secondo.
 * Questo proxy serializza tutte le chiamate tramite un [Mutex] e
 * garantisce un intervallo minimo di 1.1s tra una richiesta e la successiva.
 * Le richieste in eccesso vengono messe in coda automaticamente dal Mutex.
 *
 * Singleton: una sola istanza = un solo timer condiviso in tutta l'app.
 */
object RateLimitedNominatimProxy : NominatimApi {

    private const val TAG = "NominatimProxy"
    private const val MIN_INTERVAL_MS = 1100L // 1.1s di margine

    private val realApi: NominatimApi = NominatimClient.api
    private val mutex = Mutex()
    private var lastRequestTime = 0L

    /**
     * Esegue [block] garantendo il rispetto del rate limit.
     * Il Mutex serializza le chiamate: se una è in corso,
     * le altre aspettano in coda (FIFO).
     */
    private suspend fun <T> throttled(block: suspend () -> T): T {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTime
            if (elapsed < MIN_INTERVAL_MS) {
                val waitTime = MIN_INTERVAL_MS - elapsed
                Log.d(TAG, "Rate limit: attendo ${waitTime}ms prima della prossima richiesta")
                delay(waitTime)
            }
            val result = block()
            lastRequestTime = System.currentTimeMillis()
            Log.d(TAG, "Richiesta completata. Prossima disponibile tra ${MIN_INTERVAL_MS}ms")
            return result
        }
    }

    override suspend fun reverseGeocode(
        lat: Double,
        lon: Double,
        format: String,
        addressDetails: Int
    ): NominatimResponse {
        Log.d(TAG, "reverseGeocode in coda: lat=$lat, lon=$lon")
        return throttled { realApi.reverseGeocode(lat, lon, format, addressDetails) }
    }

    override suspend fun forwardGeocode(
        query: String,
        format: String,
        addressDetails: Int,
        limit: Int
    ): List<NominatimResponse> {
        Log.d(TAG, "forwardGeocode in coda: query=$query")
        return throttled { realApi.forwardGeocode(query, format, addressDetails, limit) }
    }
}
