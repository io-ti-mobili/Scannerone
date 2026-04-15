package com.example.scannerone.repository

import com.example.scannerone.database.WifiScanDao
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

/**
 * Il Repository funge da "Cervello" tra i Dati grezzi (DAO/Room) e la logica matematica.
 * Sgrava il ViewModel da compiti elaborativi pesanti rispettando la "Clean Architecture".
 */
class WifiScanRepository(private val dao: WifiScanDao) {

    private val scansThresholdForCompute = 5

    companion object {
        /** Max scan records kept per single network. Excess pruned by weight. */
        const val MAX_RECORDS_PER_NETWORK = 100
        /** Weight split: accuracy dominates over recency */
        private const val ACCURACY_WEIGHT = 0.8
        private const val RECENCY_WEIGHT = 0.2
    }

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

    /**
     * Calcola peso di un record: più alto = più valore = da tenere.
     * Accuracy: lower scanAccuracy (meters) = better → inverted and normalized.
     * Recency: higher timestamp = newer = better → normalized.
     * Formula: weight = ACCURACY_WEIGHT * accuracyScore + RECENCY_WEIGHT * recencyScore
     */
    private fun computeRecordWeight(record: WifiScanRecord, allRecords: List<WifiScanRecord>): Double {
        val minAcc = allRecords.minOf { it.scanAccuracy }
        val maxAcc = allRecords.maxOf { it.scanAccuracy }
        val accRange = (maxAcc - minAcc).toDouble()
        // Inverted: low accuracy value (precise GPS) → high score
        val accuracyScore = if (accRange > 0) (1.0 - (record.scanAccuracy - minAcc) / accRange) else 1.0

        val minTs = allRecords.minOf { it.timestamp }
        val maxTs = allRecords.maxOf { it.timestamp }
        val tsRange = (maxTs - minTs).toDouble()
        val recencyScore = if (tsRange > 0) ((record.timestamp - minTs).toDouble() / tsRange) else 1.0

        return ACCURACY_WEIGHT * accuracyScore + RECENCY_WEIGHT * recencyScore
    }

    /**
     * Prune scan records for a network down to [MAX_RECORDS_PER_NETWORK].
     * Keeps records with highest weight (accuracy-heavy + recency).
     */
    private suspend fun pruneRecordsIfNeeded(networkId: Int) {
        val allRecords = dao.getScansForNetwork(networkId)
        if (allRecords.size <= MAX_RECORDS_PER_NETWORK) return

        // Score every record, sort ascending by weight, delete lowest ones
        val scored = allRecords.map { it to computeRecordWeight(it, allRecords) }
        val sorted = scored.sortedBy { it.second }
        val toDelete = sorted.take(allRecords.size - MAX_RECORDS_PER_NETWORK)
        val idsToDelete = toDelete.map { it.first.id }

        dao.deleteScanRecordsByIds(idsToDelete)

        println("Pruned ${idsToDelete.size} low-weight records for networkId=$networkId")
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
            // BSSID already exists — update ESSID if changed
            internalId = dao.getNetworkIdByBssid(bssid) ?: return
            dao.updateNetworkSsid(bssid, ssid)
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

        // Prune excess records beyond cap
        pruneRecordsIfNeeded(internalId)
        
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
}
