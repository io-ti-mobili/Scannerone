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
import com.example.scannerone.services.WarDrivingService.WarDrivingConfig
import com.example.scannerone.services.nominatimApi.RateLimitedNominatimProxy
import com.example.scannerone.services.nominatimApi.toWifiNetworkFields
import kotlinx.coroutines.flow.Flow

/**
 * Il Repository funge da "Cervello" tra i Dati grezzi (DAO/Room) e la logica matematica.
 * Sgrava il ViewModel da compiti elaborativi pesanti rispettando la "Clean Architecture".
 */
class WifiScanRepository(private val dao: WifiScanDao) {

    private val scansThresholdForCompute = 5
    /**
     * Deve essere coerente con [WarDrivingConfig.MIN_ACCEPTABLE_ACCURACY_M]:
     * il wardriving persiste scansioni con accuracy fino a quel valore; un filtro
     * più stretto qui lascerebbe [WifiNetwork.realLatitude] / realLongitude sempre null.
     */
    private val maxAcceptedAccuracy = WarDrivingConfig.MIN_ACCEPTABLE_ACCURACY_M
    /** Segnali deboli ma ancora utili per centroid/trilaterazione in movimento. */
    private val minRssiForQuality = -92
    
    var config = StrategyConfig()
        set(value) {
            field = value
            updateActiveStrategy()
        }

    private var activeStrategy: LocationCalcStrategy = WeightedCentroidCalcStrategy()

    init {
        updateActiveStrategy()
    }

    suspend fun deleteNetwork(network: com.example.scannerone.entities.WifiNetwork) {
        dao.deleteNetwork(network)
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

    private suspend fun recalculateNetwork(networkId: Int) {
        val history = dao.getScansForNetwork(networkId)
        
        val highQualityScans = history.filter {
            it.scanAccuracy <= maxAcceptedAccuracy && it.rssi >= minRssiForQuality
        }

        val scansForCalc = when {
            highQualityScans.isNotEmpty() -> highQualityScans
            history.any { it.scanAccuracy <= maxAcceptedAccuracy } ->
                history.filter { it.scanAccuracy <= maxAcceptedAccuracy }
            else -> history
        }

        if (scansForCalc.isNotEmpty()) {
            val newPosition = activeStrategy.calculatePosition(scansForCalc)
            if (newPosition != null) {
                dao.updateNetworkLocation(
                    networkId = networkId,
                    lat = newPosition.latitude,
                    lon = newPosition.longitude,
                    acc = newPosition.accuracy
                )

                // Richiesta a OpenStreetMap (Nominatim) per ricavare l'indirizzo reale
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
        sessionId: Int? = null
    ) {
        val network = WifiNetwork(bssid = bssid, ssid = ssid, capabilities = capabilities, frequency = frequency)
        
        var internalId = dao.insertNetwork(network).toInt()
        if (internalId == -1) {
            internalId = dao.getNetworkIdByBssid(bssid) ?: return
        }
        
        val record = WifiScanRecord(
            networkId = internalId,
            sessionId = sessionId,
            timestamp = System.currentTimeMillis(),
            rssi = rssi,
            scanLatitude = lat,
            scanLongitude = lon,
            scanAccuracy = accuracy
        )
        dao.insertScanRecord(record)
        
        val count = dao.getScanCountForNetwork(internalId)
        
        if (count == 1 || (count > 0 && count % scansThresholdForCompute == 0)) {
            recalculateNetwork(internalId)
        }
    }

    //Funzione per recuperare le reti che stanno in una certa posizione
    suspend fun getNetworksInBoundingBox(north: Double, south: Double, east: Double, west: Double): List<WifiNetwork> {
        return dao.getNetworksInBoundingBox(north, south, east, west)
    }

    fun searchNetworksAdvanced(ssid: String, bssid: String, address: String, security: String) =
        dao.searchNetworksAdvanced(ssid, bssid, address, security)

    fun searchNetworksAdvancedPaged(
        ssid: String,
        bssid: String,
        address: String,
        security: String,
        limit: Int,
        offset: Int
    ): Flow<List<WifiNetwork>> = dao.searchNetworksAdvancedPaged(
        ssid = ssid,
        bssid = bssid,
        address = address,
        security = security,
        limit = limit,
        offset = offset
    )

    fun countNetworksAdvancedFiltered(
        ssid: String,
        bssid: String,
        address: String,
        security: String
    ): Flow<Int> = dao.countNetworksAdvancedFiltered(
        ssid = ssid,
        bssid = bssid,
        address = address,
        security = security
    )

    suspend fun hasFilteredNetworkAtOffset(
        ssid: String,
        bssid: String,
        address: String,
        security: String,
        offset: Int
    ): Boolean = dao.getNetworkIdAtFilteredOffset(
        ssid = ssid,
        bssid = bssid,
        address = address,
        security = security,
        offset = offset
    ) != null

    fun getNetworksSequence(pageSize: Int = 200): Sequence<WifiNetwork> = sequence {
        var offset = 0
        while (true) {
            val page = dao.getNetworksPaged(pageSize, offset)
            if (page.isEmpty()) break
            yieldAll(page)
            offset += pageSize
        }
    }

    fun getSessionsSequence(pageSize: Int = 200): Sequence<ScanSession> = sequence {
        var offset = 0
        while (true) {
            val page = dao.getSessionsPaged(pageSize, offset)
            if (page.isEmpty()) break
            yieldAll(page)
            offset += pageSize
        }
    }

    fun getRecordsSequence(pageSize: Int = 200): Sequence<WifiScanRecord> = sequence {
        var offset = 0
        while (true) {
            val page = dao.getRecordsPaged(pageSize, offset)
            if (page.isEmpty()) break
            yieldAll(page)
            offset += pageSize
        }
    }
}
