package com.example.scannerone.services.WarDrivingService

import android.util.Log
import com.example.scannerone.Services.ScanService.ScanService
import com.example.scannerone.entities.ScanSession
import com.example.scannerone.repository.NetworkRepository
import com.example.scannerone.repository.SessionRepository
import com.example.scannerone.services.GPSService.GPSService
import com.example.scannerone.services.GPSService.Position
import com.example.scannerone.services.motion.MotionConfig
import com.example.scannerone.services.motion.MotionState
import com.example.scannerone.services.motion.MotionStateResolver
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/**
 * Implementazione WiGLE-style del servizio di wardriving.
 *
 * Il comportamento di scansione WiFi (distanza trigger, intervallo stazionario)
 * è interamente guidato da [MotionConfig.profileFor] — nessuna costante di
 * movimento è definita qui. Per cambiare le soglie modifica [MotionConfig].
 */
class WarDrivingServiceImplV2(
    private val scanService: ScanService,
    private val gpsService: GPSService,
    private val scanRepository: NetworkRepository,
    private val sessionRepository: SessionRepository
) : WarDrivingService {

    companion object {
        private const val TAG = "WardrivingWiggle"
        private const val MIN_SCAN_COOLDOWN_MS = 2_000L
        private const val LOOP_POLL_MS = 500L
    }

    override suspend fun runSession(onResult: (WarDrivingScanResult) -> Unit) {
        val startTime = System.currentTimeMillis()
        val sessionId = sessionRepository.insertSession(ScanSession(startTime = startTime)).toInt()
        Log.d(TAG, "Sessione avviata: ID=$sessionId")

        val scope = CoroutineScope(currentCoroutineContext())
        val positionChannel = Channel<Position>(Channel.CONFLATED)

        val lock = Any()
        var lastCallbackPos: Position? = null
        var prevCallbackPos: Position? = null
        var totalDistM = 0.0
        var distSinceLastScanM = 0.0
        var lastSavedDistM = 0.0

        var scanCount = 0
        var movementScans = 0
        var stationaryScans = 0
        var lastScanTime = 0L
        val recentScanTs = ArrayList<Long>(64)

        try {
            gpsService.startContinuousUpdates { pos ->
                synchronized(lock) {
                    val prev = lastCallbackPos
                    if (prev != null && pos.accuracy < WarDrivingConfig.MIN_ACCEPTABLE_ACCURACY_M) {
                        val state = MotionStateResolver.resolve(pos, prev)

                        if (state != MotionState.Still) {
                            val dist = prev.distanceTo(pos)
                            totalDistM += dist
                            distSinceLastScanM += dist

                            if (totalDistM - lastSavedDistM >= 10.0) {
                                lastSavedDistM = totalDistM
                                val snap = totalDistM
                                scope.launch {
                                    runCatching {
                                        sessionRepository.updateSession(
                                            ScanSession(
                                                id = sessionId,
                                                startTime = startTime,
                                                distanceMetres = snap
                                            )
                                        )
                                    }.onFailure { Log.e(TAG, "Errore update distanza: ${it.message}") }
                                }
                            }
                        }

                        Log.v(TAG, "GPS: acc=${pos.accuracy}m | state=$state | distSinceScan=${"%.1f".format(distSinceLastScanM)}m")
                    }
                    prevCallbackPos = lastCallbackPos
                    lastCallbackPos = pos
                }
                positionChannel.trySend(pos)
            }

            // Attendi primo fix GPS (max 30s)
            Log.d(TAG, "Attesa primo fix GPS...")
            var waitedMs = 0L
            while (currentCoroutineContext().isActive) {
                try {
                    val pos = gpsService.getPosition()
                    synchronized(lock) { if (lastCallbackPos == null) lastCallbackPos = pos }
                    Log.d(TAG, "Primo fix: lat=${pos.latitude}, lon=${pos.longitude}, acc=${pos.accuracy}m")
                    break
                } catch (e: Exception) {
                    delay(500L); waitedMs += 500L
                    Log.w(TAG, "Attesa... (${waitedMs / 1000}s)")
                    if (waitedMs >= 30_000L) throw Exception("GPS non disponibile dopo 30s")
                }
            }

            // Prima scansione immediata
            val initialPos: Position = synchronized(lock) { lastCallbackPos!! }
            val initialDist: Double = synchronized(lock) { totalDistM }
            runCatching { performScan(sessionId, initialPos, initialDist) }
                .onSuccess { result ->
                    scanCount++; movementScans++
                    lastScanTime = System.currentTimeMillis()
                    recentScanTs.add(lastScanTime)
                    synchronized(lock) { distSinceLastScanM = 0.0 }
                    Log.d(TAG, "Scan #1 [INIZIALE] ${result.networksFound} reti")
                    onResult(result)
                }
                .onFailure { Log.e(TAG, "Errore scan iniziale: ${it.message}", it) }

            // Loop principale
            while (currentCoroutineContext().isActive) {
                withTimeoutOrNull(LOOP_POLL_MS) { positionChannel.receive() }

                val now = System.currentTimeMillis()
                val timeSinceScan: Long = now - lastScanTime

                val distSince: Double; val totalDist: Double
                val latestPos: Position?; val prevPos: Position?
                synchronized(lock) {
                    distSince = distSinceLastScanM; totalDist = totalDistM
                    latestPos = lastCallbackPos; prevPos = prevCallbackPos
                }

                val posToUse: Position = latestPos ?: continue

                // Leggi il profilo dallo stato corrente — zero logica hardcodata qui
                val state = MotionStateResolver.resolve(posToUse, prevPos)
                val profile = MotionConfig.profileFor(state)

                val cooldownOk: Boolean = timeSinceScan >= MIN_SCAN_COOLDOWN_MS
                val isMoveTrigger: Boolean = distSince >= profile.scanTriggerDistanceM
                val isTimeTrigger: Boolean = timeSinceScan >= profile.stationaryScanIntervalMs

                if (!cooldownOk || (!isMoveTrigger && !isTimeTrigger)) continue

                val label: String
                if (isMoveTrigger) { movementScans++; label = "$state (+${"%.1f".format(distSince)}m)" }
                else { stationaryScans++; label = "STAZIONARIO (${timeSinceScan / 1000}s)" }

                Log.d(TAG, "Scan #${scanCount + 1} [$label] | Δt: ${timeSinceScan}ms | Dist: ${"%.1f".format(totalDist)}m")

                runCatching { performScan(sessionId, posToUse, totalDist) }
                    .onSuccess { result ->
                        scanCount++; lastScanTime = System.currentTimeMillis()
                        synchronized(lock) { distSinceLastScanM = 0.0 }
                        recentScanTs.add(lastScanTime)
                        val cutoff: Long = lastScanTime - 60_000L
                        recentScanTs.removeAll { ts: Long -> ts < cutoff }
                        Log.d(TAG, "  ${result.networksSaved}/${result.networksFound} reti | scan/min: ${"%.1f".format(recentScanTs.size.toDouble())} | tot: $scanCount ($movementScans mov, $stationaryScans staz)")
                        onResult(result)
                    }
                    .onFailure { Log.e(TAG, "Errore scan: ${it.message}", it) }
            }

        } finally {
            gpsService.stopContinuousUpdates()
            positionChannel.close()
            val totalDuration: Long = System.currentTimeMillis() - startTime
            val finalDist: Double = synchronized(lock) { totalDistM }
            val avg: Double = if (totalDuration > 0L) scanCount.toDouble() / (totalDuration.toDouble() / 60_000.0) else 0.0
            Log.d(TAG, "Sessione $sessionId chiusa | ${totalDuration / 1000}s | $scanCount scan | ${"%.1f".format(avg)}/min | ${"%.3f".format(finalDist / 1000.0)} km")
            withContext(NonCancellable) {
                runCatching {
                    sessionRepository.updateSession(
                        ScanSession(
                            id = sessionId,
                            startTime = startTime,
                            endTime = System.currentTimeMillis(),
                            distanceMetres = finalDist
                        )
                    )
                }
                    .onFailure { Log.e(TAG, "Errore chiusura sessione: ${it.message}") }
            }
        }
    }

    private suspend fun performScan(sessionId: Int, position: Position, totalDistanceMetres: Double): WarDrivingScanResult {
        val t = System.currentTimeMillis()
        val scanResults = scanService.scan()
        Log.d(TAG, "  WiFi: ${scanResults.size} reti in ${System.currentTimeMillis() - t}ms")
        var saved = 0
        for (r in scanResults) {
            runCatching {
                scanRepository.insertScannedNetwork(r.BSSID, r.SSID ?: "", r.capabilities ?: "", r.frequency, r.level, position.latitude, position.longitude, position.accuracy, sessionId)
                saved++
            }.onFailure { Log.e(TAG, "Errore salvataggio ${r.BSSID}: ${it.message}") }
        }
        return WarDrivingScanResult(
            scanResults.size,
            saved,
            sessionRepository.getNetworksFoundInSession(sessionId),
            position,
            totalDistanceMetres,
            scanResults
        )
    }
}