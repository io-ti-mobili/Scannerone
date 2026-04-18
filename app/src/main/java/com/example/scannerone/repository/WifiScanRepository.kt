package com.example.scannerone.repository

import com.example.scannerone.database.WifiScanDao
import com.example.scannerone.entities.ScanSession
import com.example.scannerone.entities.WifiNetwork
import com.example.scannerone.entities.WifiScanRecord
import com.example.scannerone.locationCalc.LocationCalcStrategy
import com.example.scannerone.locationCalc.RansacCalcStrategyWrapper
import com.example.scannerone.locationCalc.TrilaterationCalcStrategy
import com.example.scannerone.locationCalc.WeightedCentroidCalcStrategy
import com.example.scannerone.viewmodel.StrategyConfig
import com.example.scannerone.viewmodel.StrategyType
import com.example.scannerone.utils.categorizeNetwork
import com.example.scannerone.services.nominatimApi.RateLimitedNominatimProxy
import com.example.scannerone.services.nominatimApi.toWifiNetworkFields
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel

/**
 * Il Repository funge da "Cervello" tra i Dati grezzi (DAO/Room) e la logica matematica.
 * Sgrava il ViewModel da compiti elaborativi pesanti rispettando la "Clean Architecture".
 */
class WifiScanRepository(private val dao: WifiScanDao) {

    private val scansThresholdForCompute = 5
    private val maxAcceptedAccuracy = 20.0f
    private val MIN_RSSI = -85

    // Scope dedicato per task di background (ricalcolo e geocoding)
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    var config = StrategyConfig()
        set(value) {
            field = value
            updateActiveStrategy()
        }

    private var activeStrategy: LocationCalcStrategy = WeightedCentroidCalcStrategy()

    // Coda di elaborazione per processare le reti una alla volta
    private val calculationChannel = Channel<Int>(Channel.UNLIMITED)

    init {
        updateActiveStrategy()
        
        // Avviamo il worker che consuma la coda in modo sequenziale
        repositoryScope.launch {
            for (networkId in calculationChannel) {
                recalculateNetwork(networkId)
            }
        }
    }


    private fun updateActiveStrategy() {
        var baseStrategy: LocationCalcStrategy = when (config.baseStrategyType) {
            StrategyType.CENTROID -> WeightedCentroidCalcStrategy(useGpsWeight = config.useGpsWeight)
            StrategyType.TRILATERATION -> TrilaterationCalcStrategy(useGpsWeight = config.useGpsWeight)
        }
        
        // Pura magia del Decorator Pattern: Avvolgiamo la strategia se richiesto!
        if (config.useRansac) {
            baseStrategy = RansacCalcStrategyWrapper(baseStrategy)
        }
        
        activeStrategy = baseStrategy
    }

    val networks = dao.getAllNetworks()
    
    suspend fun recalculateAllNetworks() {
        val networkIds = dao.getAllNetworkIds()
        for (id in networkIds) {
            recalculateNetwork(id)
        }
    }

    suspend fun recalculateNetworks(networkIds: List<Int>) {
        for (id in networkIds) {
            recalculateNetwork(id)
        }
    }

    private val MAX_SCANS_FOR_COMPUTE = 50

    private suspend fun recalculateNetwork(networkId: Int) {
        val history = dao.getScansForNetwork(networkId)
        
        val highQualityScans = history.filter {
            it.scanAccuracy <= maxAcceptedAccuracy && it.rssi >= MIN_RSSI 
        }
        
        // Mantieni tutta la history nel DB, ma seleziona i migliori "N" per il calcolo
        val bestScansForCompute = highQualityScans
            .sortedByDescending { it.timestamp.toFloat() / (it.scanAccuracy + 1f) }
            .take(MAX_SCANS_FOR_COMPUTE)
        
        if (bestScansForCompute.isNotEmpty()) {
            val newPosition = activeStrategy.calculatePosition(bestScansForCompute)
            if (newPosition != null) {
                dao.updateNetworkLocation(
                    networkId = networkId,
                    lat = newPosition.latitude,
                    lon = newPosition.longitude,
                    acc = newPosition.accuracy
                )
                
                // Richiesta a OpenStreetMap per ricavare l'indirizzo reale
                fetchAndSetNetworkAddress(networkId, newPosition.latitude, newPosition.longitude)
            }
        }
    }

    private suspend fun fetchAndSetNetworkAddress(networkId: Int, lat: Double, lon: Double) {
        try {
            println("=== DEBUG NOMINATIM ===")
            println("Richiesta coordinate: Lat: $lat, Lon: $lon")
            
            val response = RateLimitedNominatimProxy.reverseGeocode(lat, lon)
            println("Risposta Nominatim: $response")
            
            val fields = response.toWifiNetworkFields()
            println("Campi estratti per il Database: $fields")
            println("=======================")
            
            dao.updateNetworkAddressDetails(
                networkId = networkId,
                street = fields["realStreet"],
                city = fields["realCity"],
                region = fields["realRegion"],
                country = fields["realCountry"]
            )
        } catch (e: Exception) {
            println("Errore Nominatim: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun insertScannedNetwork(
        bssid: String,
        ssid: String,
        capabilities: String,
        frequency: Int,
        rssi: Int,
        lat: Double,
        lon: Double,
        accuracy: Float,
        sessionId: Int? = null,
        forcedTimestamp: Long? = null
    ) {
        val cat = categorizeNetwork(ssid).name
        
        val capUpper = capabilities.uppercase()
        val securityStr = when {
            capUpper.contains("WPA3") || capUpper.contains("OWE") || capUpper.contains("SAE") -> "WPA3"
            capUpper.contains("WPA2") -> "WPA2"
            capUpper.contains("WPA-") || capUpper.contains("WPA1") -> "WPA"
            capUpper.contains("WEP") -> "WEP"
            capUpper.isEmpty() || capUpper == "[ESS]" || capUpper.contains("OPEN") || capUpper.contains("NONE") -> "Open"
            else -> "Altro"
        }

        val band = when {
            frequency in 2400..2500 -> 2.4f
            frequency in 5000..5900 -> 5.0f
            frequency > 5900 -> 6.0f
            else -> 0.0f
        }
        
        val network = WifiNetwork(
            bssid = bssid, 
            ssid = ssid, 
            capabilities = capabilities, 
            frequency = frequency, 
            category = cat,
            security = securityStr,
            frequencyBand = band
        )
        
        var internalId = dao.insertNetwork(network).toInt()
        val isFirst = internalId != -1
        if (internalId == -1) {
            internalId = dao.getNetworkIdByBssid(bssid) ?: return
        }
        
        val record = WifiScanRecord(
            networkId = internalId,
            sessionId = sessionId,
            timestamp = forcedTimestamp ?: System.currentTimeMillis(),
            rssi = rssi,
            scanLatitude = lat,
            scanLongitude = lon,
            scanAccuracy = accuracy,
            isFirstDiscovery = isFirst
        )
        dao.insertScanRecord(record)
        
        val count = dao.getScanCountForNetwork(internalId)
        
        if (count == 1 || (count > 0 && count % scansThresholdForCompute == 0)) {
            // Inviamo l'ID della rete nella coda di elaborazione sequenziale
            calculationChannel.trySend(internalId)
        }
    }
    suspend fun deleteNetwork(network: WifiNetwork) {
        dao.deleteNetwork(network)
    }

    suspend fun getNetworksInBoundingBox(north: Double, south: Double, east: Double, west: Double): List<WifiNetwork> {
        return dao.getNetworksInBoundingBox(north, south, east, west)
    }
    fun searchNetworksAdvanced(ssid: String, bssid: String, address: String, security: String) =
        dao.searchNetworksAdvanced(ssid, bssid, address, security)
    fun getTotalNetworksCount() = dao.getTotalNetworksCount()
    fun getTotalScansCount() = dao.getTotalScansCount()
    fun getTotalDistance() = dao.getTotalDistance()
    fun getTotalTime() = dao.getTotalTime()
    fun getDiscoveryTrendStats(startTime: Long, endTime: Long, bucketSize: Long) = dao.getDiscoveryTrendStats(startTime, endTime, bucketSize)
    fun getScanTrendStats(startTime: Long, endTime: Long, bucketSize: Long) = dao.getScanTrendStats(startTime, endTime, bucketSize)
    fun getSessionTrendStats(startTime: Long, endTime: Long, bucketSize: Long) = dao.getSessionTrendStats(startTime, endTime, bucketSize)
    fun getSessionWithMostUniqueNetworks() = dao.getSessionWithMostUniqueNetworks()
    fun getLongestSession() = dao.getLongestSession()
    fun getMostDistanceSession() = dao.getMostDistanceSession()
    fun getSessionTotalScansCountFlow(sessionId: Int?) = dao.getSessionTotalScansCountFlow(sessionId)
    fun getSessionDiscoveryCountFlow(sessionId: Int?) = dao.getSessionDiscoveryCountFlow(sessionId)
    fun getSessionUniqueNetworksCountFlow(sessionId: Int?) = dao.getSessionUniqueNetworksCountFlow(sessionId)
    fun getCategoryStatsFlow(sessionId: Int?) = dao.getCategoryStatsFlow(sessionId)
    fun getSecurityStatsFlow(sessionId: Int?) = dao.getSecurityStatsFlow(sessionId)
    fun getFrequencyStatsFlow(sessionId: Int?) = dao.getFrequencyStatsFlow(sessionId)
    fun getAllSessions() = dao.getAllSessions()
    fun getScanRecordsForSession(sessionId: Int?) = dao.getScanRecordsForSession(sessionId)

    suspend fun insertSession(session: ScanSession): Long = dao.insertSession(session)
    suspend fun updateSession(session: ScanSession) = dao.updateSession(session)

    suspend fun updateSessionDistance(sessionId: Int, distance: Double) {
        dao.updateSessionDistance(sessionId, distance)
    }
}
