package com.example.scannerone.services.WarDrivingService

import android.util.Log
import com.example.scannerone.Services.ScanService.ScanService
import com.example.scannerone.database.WifiScanDao
import com.example.scannerone.entities.ScanSession
import com.example.scannerone.repository.WifiScanRepository
import com.example.scannerone.services.GPSService.GPSService
import com.example.scannerone.services.GPSService.Position
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/**
 * Implementazione WiGLE-style del servizio di wardriving.
 *
 * A differenza di Impl1 (timer fisso) e Impl2 (GPS-driven via Channel conflated),
 * qui è il MOVIMENTO REALE a triggerare la scansione WiFi:
 *
 *   - Movimento rilevato → scan dopo ogni [SCAN_TRIGGER_DISTANCE_M] metri percorsi
 *   - Fermo → scan ogni [STATIONARY_SCAN_INTERVAL_MS] (molto meno frequente)
 *   - Mai più di 1 scan ogni [MIN_SCAN_COOLDOWN_MS] in ogni caso
 *
 * Note architetturali:
 *
 * Threading: il GPS callback gira su un thread diverso dalla coroutine
 * (HandlerThread con V2, main thread con V1). Le variabili condivise
 * (distanza, posizione) sono protette da [lock] tramite synchronized{}.
 *
 * Channel: il positionChannel è CONFLATED. La distanza si accumula nel callback
 * (lock), quindi non si perde anche se il channel scarta posizioni intermedie.
 * La coroutine usa il channel solo per sapere che è arrivato un fix nuovo.
 *
 * Scan stazionaria: non serve un secondo timer coroutine. Ogni iterazione del
 * loop controlla (now - lastScanTime >= STATIONARY_SCAN_INTERVAL_MS).
 */
class WarDrivingServiceImplWiggle(
    private val scanService: ScanService,
    private val gpsService: GPSService,
    private val repository: WifiScanRepository,
    private val dao: WifiScanDao
) : WarDrivingService {

    companion object {
        private const val TAG = "WardrivingWiggle"

        /** Distanza da percorrere per triggerare una scan WiFi */
        private const val SCAN_TRIGGER_DISTANCE_M = 10.0

        /** Intervallo massimo senza scan anche se il dispositivo è fermo */
        private const val STATIONARY_SCAN_INTERVAL_MS = 30_000L

        /** Cooldown minimo assoluto tra scan consecutive */
        private const val MIN_SCAN_COOLDOWN_MS = 2_000L

        /** Velocità minima per considerare il dispositivo in movimento (m/s ≈ 1.8 km/h) */
        private const val MOVING_SPEED_THRESHOLD = 0.5f

        /**
         * Timeout del loop principale.
         * Abbastanza corto da reagire al movimento, abbastanza lungo da non fare busy-loop.
         * Con GPS ogni 500ms, ogni iterazione trova quasi sempre un fix nel channel.
         */
        private const val LOOP_POLL_MS = 500L
    }

    override suspend fun runSession(onResult: (WarDrivingScanResult) -> Unit) {
        // FIX: startTime usato sia per il DB che per calcolare la durata totale in finally.
        // Nella versione precedente c'era sessionStart (non definito) nel finally.
        val startTime = System.currentTimeMillis()
        val sessionId = dao.insertSession(ScanSession(startTime = startTime)).toInt()
        Log.d(TAG, "═══ Sessione WiGLE-style avviata: ID=$sessionId ═══")
        Log.d(TAG, "    Trigger: ogni ${SCAN_TRIGGER_DISTANCE_M}m | " +
                "Stazionario: ogni ${STATIONARY_SCAN_INTERVAL_MS / 1000}s | " +
                "Cooldown: ${MIN_SCAN_COOLDOWN_MS}ms")

        val scope = CoroutineScope(currentCoroutineContext())
        val positionChannel = Channel<Position>(Channel.CONFLATED)

        // ── Stato condiviso callback ↔ coroutine ─────────────────────────────
        val lock = Any()
        var lastCallbackPos: Position? = null
        var totalDistM = 0.0
        var distSinceLastScanM = 0.0
        var lastSavedDistM = 0.0

        // ── Stats (solo dalla coroutine → nessun lock) ────────────────────────
        var scanCount = 0
        var movementScans = 0
        var stationaryScans = 0
        var lastScanTime = 0L

        // FIX compareTo: ArrayList<Long> con tipo esplicito nel lambda di removeAll.
        // ArrayDeque<Long> usa Long boxato (generic) e in certi contesti Kotlin
        // non risolve '>=' a un operatore primitivo, generando:
        // "'operator' modifier is required on compareTo".
        // Con ArrayList<Long> e lambda tipizzato il problema non si presenta.
        val recentScanTs = ArrayList<Long>(64)

        try {
            // ── Avvia GPS continuo ───────────────────────────────────────────
            gpsService.startContinuousUpdates { pos ->
                synchronized(lock) {
                    val prev = lastCallbackPos

                    if (prev != null && pos.accuracy < WarDrivingConfig.MIN_ACCEPTABLE_ACCURACY_M) {
                        val dist = prev.distanceTo(pos)

                        val isMoving = if (pos.hasSpeed) {
                            pos.speed > MOVING_SPEED_THRESHOLD
                        } else {
                            // Un dispositivo fermo genera rumore di 1-2m tra fix consecutivi.
                            // Soglia 2.5m filtra il rumore senza perdere movimento reale.
                            dist > 2.5
                        }

                        if (isMoving) {
                            totalDistM += dist
                            distSinceLastScanM += dist

                            if (totalDistM - lastSavedDistM >= 10.0) {
                                lastSavedDistM = totalDistM
                                val distSnapshot = totalDistM
                                scope.launch {
                                    runCatching {
                                        dao.updateSession(
                                            ScanSession(
                                                id = sessionId,
                                                startTime = startTime,
                                                distanceMetres = distSnapshot
                                            )
                                        )
                                    }.onFailure { Log.e(TAG, "Errore update distanza DB: ${it.message}") }
                                }
                            }
                        }
                    }

                    lastCallbackPos = pos
                }

                positionChannel.trySend(pos)
                Log.v(
                    TAG,
                    "GPS: acc=${pos.accuracy}m | speed=${pos.speed}m/s | " +
                            "distSinceScan=${"%.1f".format(synchronized(lock) { distSinceLastScanM })}m"
                )
            }

            // ── Attendi primo fix GPS (max 30s) ──────────────────────────────
            Log.d(TAG, "⏳ Attesa primo fix GPS...")
            var waitedMs = 0L
            while (currentCoroutineContext().isActive) {
                try {
                    val pos = gpsService.getPosition()
                    synchronized(lock) { if (lastCallbackPos == null) lastCallbackPos = pos }
                    Log.d(TAG, "✓ Primo fix: lat=${pos.latitude}, lon=${pos.longitude}, acc=${pos.accuracy}m")
                    break
                } catch (e: Exception) {
                    delay(500L)
                    waitedMs += 500L
                    Log.w(TAG, "Attesa fix GPS... (${waitedMs / 1000}s)")
                    if (waitedMs >= 30_000L) throw Exception("GPS non disponibile dopo 30s")
                }
            }

            // ── Prima scansione immediata ────────────────────────────────────
            val initialPos: Position = synchronized(lock) { lastCallbackPos!! }
            val initialDist: Double = synchronized(lock) { totalDistM }
            runCatching { performScan(sessionId, initialPos, initialDist) }
                .onSuccess { result ->
                    scanCount++
                    movementScans++
                    lastScanTime = System.currentTimeMillis()
                    recentScanTs.add(lastScanTime)
                    synchronized(lock) { distSinceLastScanM = 0.0 }
                    Log.d(TAG, "── Scan #1 [INIZIALE] ── ${result.networksFound} reti trovate")
                    onResult(result)
                }
                .onFailure { Log.e(TAG, "Errore scan iniziale: ${it.message}", it) }

            // ── Loop principale ──────────────────────────────────────────────
            while (currentCoroutineContext().isActive) {

                // Attendi un fix GPS o procedi dopo timeout (per controllo stazionario)
                withTimeoutOrNull(LOOP_POLL_MS) { positionChannel.receive() }

                val now = System.currentTimeMillis()
                val timeSinceScan: Long = now - lastScanTime

                // Leggi stato condiviso tutto in un blocco solo
                val distSince: Double
                val totalDist: Double
                val latestPos: Position?
                synchronized(lock) {
                    distSince = distSinceLastScanM
                    totalDist = totalDistM
                    latestPos = lastCallbackPos
                }

                val posToUse: Position = latestPos ?: continue

                val cooldownOk: Boolean = timeSinceScan >= MIN_SCAN_COOLDOWN_MS
                val isMoveTrigger: Boolean = distSince >= SCAN_TRIGGER_DISTANCE_M
                val isTimeTrigger: Boolean = timeSinceScan >= STATIONARY_SCAN_INTERVAL_MS

                if (!cooldownOk || (!isMoveTrigger && !isTimeTrigger)) continue

                val label: String
                if (isMoveTrigger) {
                    movementScans++
                    label = "MOVIMENTO (+${"%.1f".format(distSince)}m)"
                } else {
                    stationaryScans++
                    label = "STAZIONARIO (${timeSinceScan / 1000}s senza scan)"
                }

                Log.d(TAG, "── Scan #${scanCount + 1} [$label] ──")
                Log.d(TAG, "   Δt dall'ultima scan: ${timeSinceScan}ms | Dist tot: ${"%.1f".format(totalDist)}m")

                runCatching { performScan(sessionId, posToUse, totalDist) }
                    .onSuccess { result ->
                        scanCount++
                        lastScanTime = System.currentTimeMillis()
                        synchronized(lock) { distSinceLastScanM = 0.0 }

                        // Rolling window scan/min (ultimi 60s)
                        recentScanTs.add(lastScanTime)
                        val cutoff: Long = lastScanTime - 60_000L
                        recentScanTs.removeAll { ts: Long -> ts < cutoff }

                        Log.d(TAG, "   ✓ ${result.networksSaved}/${result.networksFound} reti salvate | " +
                                "uniche: ${result.uniqueNetworksInSession}")
                        Log.d(TAG, "   scan/min (60s): ${"%.1f".format(recentScanTs.size.toDouble())} | " +
                                "Totale: $scanCount ($movementScans mov, $stationaryScans staz)")
                        onResult(result)
                    }
                    .onFailure { Log.e(TAG, "Errore durante scan: ${it.message}", it) }
            }

        } finally {
            gpsService.stopContinuousUpdates()
            positionChannel.close()

            val totalDuration: Long = System.currentTimeMillis() - startTime
            val finalDist: Double = synchronized(lock) { totalDistM }
            val avgScansPerMin: Double =
                if (totalDuration > 0L) scanCount.toDouble() / (totalDuration.toDouble() / 60_000.0)
                else 0.0

            Log.d(TAG, "═══ Sessione $sessionId chiusa ═══")
            Log.d(TAG, "    Durata: ${totalDuration / 1000}s")
            Log.d(TAG, "    Scan: $scanCount ($movementScans movimento, $stationaryScans stazionari)")
            Log.d(TAG, "    Media: ${"%.1f".format(avgScansPerMin)} scan/min")
            Log.d(TAG, "    Distanza: ${"%.3f".format(finalDist / 1000.0)} km")

            withContext(NonCancellable) {
                runCatching {
                    dao.updateSession(
                        ScanSession(
                            id = sessionId,
                            startTime = startTime,
                            endTime = System.currentTimeMillis(),
                            distanceMetres = finalDist
                        )
                    )
                }.onFailure { Log.e(TAG, "Errore chiusura sessione $sessionId: ${it.message}") }
            }
        }
    }

    // ── Scansione singola ─────────────────────────────────────────────────────

    private suspend fun performScan(
        sessionId: Int,
        position: Position,
        totalDistanceMetres: Double
    ): WarDrivingScanResult {
        val scanStart = System.currentTimeMillis()
        val scanResults = scanService.scan()
        val scanDuration: Long = System.currentTimeMillis() - scanStart
        Log.d(TAG, "   WiFi: ${scanResults.size} reti trovate in ${scanDuration}ms")

        var savedCount = 0
        for (r in scanResults) {
            runCatching {
                repository.insertScannedNetwork(
                    bssid = r.BSSID,
                    ssid = r.SSID ?: "",
                    capabilities = r.capabilities ?: "",
                    frequency = r.frequency,
                    rssi = r.level,
                    lat = position.latitude,
                    lon = position.longitude,
                    accuracy = position.accuracy,
                    sessionId = sessionId
                )
                savedCount++
            }.onFailure { Log.e(TAG, "Errore salvataggio ${r.BSSID}: ${it.message}") }
        }

        return WarDrivingScanResult(
            networksFound = scanResults.size,
            networksSaved = savedCount,
            uniqueNetworksInSession = dao.getNetworksFoundInSession(sessionId),
            position = position,
            totalDistanceMetres = totalDistanceMetres,
            scanResults = scanResults
        )
    }
}