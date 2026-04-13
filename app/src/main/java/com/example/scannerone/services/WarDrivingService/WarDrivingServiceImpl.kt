package com.example.scannerone.services.WarDrivingService

import android.util.Log
import com.example.scannerone.Services.ScanService.ScanService
import com.example.scannerone.database.WifiScanDao
import com.example.scannerone.entities.ScanSession
import com.example.scannerone.repository.WifiScanRepository
import com.example.scannerone.services.GPSService.GPSService
import com.example.scannerone.services.GPSService.Position
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.ArrayDeque

class WarDrivingServiceImpl(
    private val scanService: ScanService,
    private val gpsService: GPSService,
    private val repository: WifiScanRepository,
    private val dao: WifiScanDao
) : WarDrivingService {

    companion object {
        private const val TAG = "WarDrivingService"
    }

    private val gpsBuffer = ArrayDeque<Position>(WarDrivingConfig.GPS_BUFFER_SIZE)

    // ── GPS buffer ────────────────────────────────────────────────────────────

    @Synchronized
    fun addGPSPosition(position: Position) {
        if (gpsBuffer.size >= WarDrivingConfig.GPS_BUFFER_SIZE) {
            val removed = gpsBuffer.removeFirst()
            Log.d(TAG, "Buffer GPS pieno, rimossa posizione vecchia (age=${removed.getAge()}ms)")
        }
        gpsBuffer.addLast(position)
        Log.d(TAG, "Posizione aggiunta al buffer: acc=${position.accuracy}m, buffer_size=${gpsBuffer.size}")
    }

    @Synchronized
    private fun getBestRecentPosition(): Position {
        if (gpsBuffer.isEmpty()) {
            throw Exception("Buffer GPS vuoto. Nessuna posizione disponibile.")
        }

        data class RankedPosition(
            val position: Position,
            val age: Long,
            val isPreferredAccuracy: Boolean
        )

        val rankedPositions = gpsBuffer.mapNotNull { pos ->
            val age = pos.getAge()

            if (age > WarDrivingConfig.MAX_GPS_AGE_MS) {
                Log.w(TAG, "⚠️ Posizione GPS troppo vecchia: age=${age}ms (max=${WarDrivingConfig.MAX_GPS_AGE_MS}ms)")
                return@mapNotNull null
            }

            if (pos.accuracy > WarDrivingConfig.MIN_ACCEPTABLE_ACCURACY_M) {
                Log.w(TAG, "⚠️ Posizione GPS troppo imprecisa: acc=${pos.accuracy}m (max=${WarDrivingConfig.MIN_ACCEPTABLE_ACCURACY_M}m)")
                return@mapNotNull null
            }

            RankedPosition(
                position = pos,
                age = age,
                isPreferredAccuracy = pos.accuracy <= WarDrivingConfig.PREFERRED_ACCURACY_M
            )
        }

        if (rankedPositions.isEmpty()) {
            val fallback = gpsBuffer.last()
            Log.w(TAG, "⚠️ Nessuna posizione GPS ottimale, uso fallback: age=${fallback.getAge()}ms, acc=${fallback.accuracy}m")
            return fallback
        }

        val best = rankedPositions.sortedWith(
            compareByDescending<RankedPosition> { it.isPreferredAccuracy }
                .thenBy { it.age }
                .thenBy { it.position.accuracy }
        ).first()

        Log.d(TAG, "✓ Posizione GPS selezionata: age=${best.age}ms, acc=${best.position.accuracy}m" +
                   "${if (best.isPreferredAccuracy) " [PREFERRED]" else ""}")

        return best.position
    }

    // ── Sessione ──────────────────────────────────────────────────────────────

    /**
     * Avvia una sessione completa di wardriving.
     * Crea la sessione nel DB, esegue le scansioni in loop e la chiude
     * automaticamente quando la coroutine viene cancellata (try/finally).
     */
    override suspend fun runSession(onResult: (WarDrivingScanResult) -> Unit) {
        val startTime = System.currentTimeMillis()
        val sessionId = dao.insertSession(ScanSession(startTime = startTime)).toInt()
        Log.d(TAG, "Nuova sessione creata: ID=$sessionId")

        var totalDistanceMetres = 0.0
        var lastPosition: Position? = null

        try {
            gpsService.startContinuousUpdates { position ->
                addGPSPosition(position)

                if (position.accuracy < 50) {
                    if (lastPosition == null) {
                        lastPosition = position
                    } else {
                        val prev = lastPosition!!
                        val dist = prev.distanceTo(position)
                        
                        // Determina se il dispositivo si sta effettivamente muovendo
                        // Usa la velocità hardware del GPS invece della semplice distanza)
                        val isMoving = if (position.hasSpeed) {
                            // Se il sensore GPS riporta una velocità < 0.3 m/s (1.08 km/h), consideriamo l'utente fermo
                            position.speed > 0.3f
                        } else {
                            // Fallback: se la velocità hardware non è disponibile, filtriamo il rumore
                            // basandoci su uno spostamento minimo e ragionevole
                            dist > 2.5
                        }

                        if (isMoving) {
                            totalDistanceMetres += dist
                            lastPosition = position
                        }
                    }
                }
                Log.d(TAG, "GPS fix aggiunto al buffer: acc=${position.accuracy}m | Dist: ${"%.2f".format(totalDistanceMetres)}m")
            }

            Log.d(TAG, "Wardriving avviato (GPS: ${WarDrivingConfig.GPS_UPDATE_INTERVAL_MS}ms, Scan: ${WarDrivingConfig.SCAN_INTERVAL_MS}ms)")

            // Attendi il primo fix GPS
            Log.d(TAG, "Attesa primo fix GPS...")
            var waitTime = 0L
            val maxWaitTime = 30_000L
            while (currentCoroutineContext().isActive) {
                try {
                    val initialPosition = gpsService.getPosition()
                    Log.d(TAG, "✓ Primo fix GPS: lat=${initialPosition.latitude}, lon=${initialPosition.longitude}, acc=${initialPosition.accuracy}m")
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Attesa fix GPS... (${waitTime / 1000}s)")
                    delay(1000L)
                    waitTime += 1000L
                    if (waitTime >= maxWaitTime) {
                        Log.e(TAG, "Timeout attesa primo fix GPS dopo ${maxWaitTime / 1000}s")
                        throw Exception("GPS non disponibile dopo ${maxWaitTime / 1000}s")
                    }
                }
            }

            // Loop principale di scansione
            while (currentCoroutineContext().isActive) {
                val cycleStartTime = System.currentTimeMillis()
                try {
                    val result = performScan(sessionId, totalDistanceMetres)
                    onResult(result)
                } catch (e: Exception) {
                    Log.e(TAG, "Errore durante la scansione: ${e.message}", e)
                } finally {
                    val cycleDuration = System.currentTimeMillis() - cycleStartTime
                    val waitMs = maxOf(0L, WarDrivingConfig.SCAN_INTERVAL_MS - cycleDuration)
                    if (waitMs == 0L) Log.w(TAG, "⚠️ Ciclo lento (${cycleDuration}ms > ${WarDrivingConfig.SCAN_INTERVAL_MS}ms)")
                    delay(waitMs)
                }
            }
        } finally {
            gpsService.stopContinuousUpdates()

            // Chiudi la sessione con i dati finali
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    dao.updateSession(
                        ScanSession(
                            id = sessionId,
                            startTime = startTime,
                            endTime = System.currentTimeMillis(),
                            distanceMetres = totalDistanceMetres
                        )
                    )
                }
                Log.d(TAG, "Sessione $sessionId chiusa: ${"%.3f".format(totalDistanceMetres / 1000.0)} km percorsi")
            } catch (e: Exception) {
                Log.e(TAG, "Errore chiusura sessione $sessionId: ${e.message}")
            }
        }
    }

    // ── Scansione singola (privata) ───────────────────────────────────────────

    private suspend fun performScan(sessionId: Int, totalDistanceMetres: Double): WarDrivingScanResult {
        Log.d(TAG, "Selezione migliore posizione GPS dal buffer...")
        val position = getBestRecentPosition()
        Log.d(TAG, "Posizione GPS confermata: lat=${position.latitude}, lon=${position.longitude}, " +
                   "acc=${position.accuracy}m, age=${position.getAge()}ms")

        Log.d(TAG, "Avvio scansione Wi-Fi...")
        val scanStartTime = System.currentTimeMillis()
        val scanResults = scanService.scan()
        val scanDuration = System.currentTimeMillis() - scanStartTime
        Log.d(TAG, "Scansione completata: ${scanResults.size} reti trovate (durata: ${scanDuration}ms)")

        var savedCount = 0
        for (result in scanResults) {
            try {
                repository.insertScannedNetwork(
                    bssid = result.BSSID,
                    ssid = result.SSID ?: "",
                    capabilities = result.capabilities ?: "",
                    frequency = result.frequency,
                    rssi = result.level,
                    lat = position.latitude,
                    lon = position.longitude,
                    accuracy = position.accuracy,
                    sessionId = sessionId
                )
                savedCount++
            } catch (e: Exception) {
                Log.e(TAG, "Errore nel salvataggio della rete ${result.BSSID}: ${e.message}")
            }
        }

        Log.d(TAG, "Ciclo completato: $savedCount/${scanResults.size} reti salvate")

        val uniqueNetworks = dao.getNetworksFoundInSession(sessionId)

        return WarDrivingScanResult(
            networksFound = scanResults.size,
            networksSaved = savedCount,
            uniqueNetworksInSession = uniqueNetworks,
            position = position,
            totalDistanceMetres = totalDistanceMetres
        )
    }
}
