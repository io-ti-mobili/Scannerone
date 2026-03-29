package com.example.scannerone.services.WarDrivingService

import android.util.Log
import com.example.scannerone.Services.ScanService.ScanService
import com.example.scannerone.repository.WifiScanRepository
import com.example.scannerone.services.GPSService.GPSService

/**
 * Implementazione concreta di [WarDrivingService].
 *
 * Tutte le dipendenze vengono iniettate via costruttore,
 * rendendo il servizio testabile e disaccoppiato.
 */
class WarDrivingServiceImpl(
    private val scanService: ScanService,
    private val gpsService: GPSService,
    private val repository: WifiScanRepository
) : WarDrivingService {

    companion object {
        private const val TAG = "WarDrivingService"
    }

    override suspend fun performScan(): WarDrivingScanResult {
        // 1. Ottieni la posizione GPS corrente
        Log.d(TAG, "Richiesta posizione GPS...")
        val position = gpsService.getPosition()
        Log.d(TAG, "Posizione ottenuta: lat=${position.latitude}, lon=${position.longitude}, acc=${position.accuracy}")

        // 2. Esegui la scansione Wi-Fi
        Log.d(TAG, "Avvio scansione Wi-Fi...")
        val scanResults = scanService.scan()
        Log.d(TAG, "Scansione completata: ${scanResults.size} reti trovate")

        // 3. Salva ogni rete nel database, associata alla posizione corrente
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
                    accuracy = position.accuracy
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
