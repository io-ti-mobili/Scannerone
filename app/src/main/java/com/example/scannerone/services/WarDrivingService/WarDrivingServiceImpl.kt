package com.example.scannerone.services.WarDrivingService

import android.util.Log
import com.example.scannerone.Services.ScanService.ScanService
import com.example.scannerone.repository.WifiScanRepository
import com.example.scannerone.services.GPSService.GPSService
import com.example.scannerone.services.GPSService.Position
import java.util.ArrayDeque

class WarDrivingServiceImpl(
    private val scanService: ScanService,
    private val gpsService: GPSService,
    private val repository: WifiScanRepository
) : WarDrivingService {

    companion object {
        private const val TAG = "WarDrivingService"
    }
    
    private val gpsBuffer = ArrayDeque<Position>(WarDrivingConfig.GPS_BUFFER_SIZE)
    
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

    override suspend fun performScan(sessionId: Int?): WarDrivingScanResult {
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

        return WarDrivingScanResult(
            networksFound = scanResults.size,
            networksSaved = savedCount,
            position = position
        )
    }
}
