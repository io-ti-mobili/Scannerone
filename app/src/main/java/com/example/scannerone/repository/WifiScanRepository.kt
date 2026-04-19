package com.example.scannerone.repository

import androidx.room.withTransaction
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
import com.example.scannerone.utils.categorizeNetwork
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

    companion object {
        /** Max scan records kept per single network. Excess pruned by weight. */
        const val MAX_RECORDS_PER_NETWORK = 100
        /** Weight split: accuracy dominates over recency */
        private const val ACCURACY_WEIGHT = 0.8
        private const val RECENCY_WEIGHT = 0.2
    }

    /**
     * Semplifichiamo le soglie di qualità.
     */
    private val maxAcceptedAccuracy = WarDrivingConfig.MIN_ACCEPTABLE_ACCURACY_M
    private val minRssiForQuality = -92

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

        if (history.isEmpty()) return
        var usableScans = history.filter {
            it.scanAccuracy <= maxAcceptedAccuracy
        }

        // 2. FALLBACK (Il Salvagente):
        // Se TUTTE le scansioni di questa rete avevano un GPS pessimo (es. eri al chiuso
        // o sotto alberi fitti), usableScans sarebbe vuota e la rete scomparirebbe.
        // In questo caso, usiamo l'intero storico: è meglio avere una rete posizionata
        // in modo approssimativo (con precisione bassa) che non averla affatto!
        if (usableScans.isEmpty()) {
            usableScans = history
        }

        // 3. Seleziona i migliori "N" per il calcolo basandosi su recency/accuracy
        val bestScansForCompute = usableScans
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

                // Richiesta a OpenStreetMap (Nominatim) per ricavare l'indirizzo reale
                fetchAndSetNetworkAddress(networkId, newPosition.latitude, newPosition.longitude)
            }
        }
    }

    private suspend fun fetchAndSetNetworkAddress(networkId: Int, lat: Double, lon: Double) {
        try {
            val response = RateLimitedNominatimProxy.reverseGeocode(lat, lon)
            val fields = response.toWifiNetworkFields()
            
            dao.updateNetworkAddressDetails(
                networkId = networkId,
                street = fields["realStreet"],
                city = fields["realCity"],
                region = fields["realRegion"],
                country = fields["realCountry"]
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun computeRecordWeight(record: WifiScanRecord, allRecords: List<WifiScanRecord>): Double {
        val minAcc = allRecords.minOf { it.scanAccuracy }
        val maxAcc = allRecords.maxOf { it.scanAccuracy }
        val accRange = (maxAcc - minAcc).toDouble()
        val accuracyScore = if (accRange > 0) (1.0 - (record.scanAccuracy - minAcc) / accRange) else 1.0

        val minTs = allRecords.minOf { it.timestamp }
        val maxTs = allRecords.maxOf { it.timestamp }
        val tsRange = (maxTs - minTs).toDouble()
        val recencyScore = if (tsRange > 0) ((record.timestamp - minTs).toDouble() / tsRange) else 1.0

        return ACCURACY_WEIGHT * accuracyScore + RECENCY_WEIGHT * recencyScore
    }

    private suspend fun pruneRecordsIfNeeded(networkId: Int) {
        val allRecords = dao.getScansForNetwork(networkId)
        if (allRecords.size <= MAX_RECORDS_PER_NETWORK) return

        val scored = allRecords.map { it to computeRecordWeight(it, allRecords) } 
        val sorted = scored.sortedBy { it.second }
        val toDelete = sorted.take(allRecords.size - MAX_RECORDS_PER_NETWORK)
        val idsToDelete = toDelete.map { it.first.id }

        dao.deleteScanRecordsByIds(idsToDelete)
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
            dao.updateNetworkSsid(bssid, ssid)
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
        pruneRecordsIfNeeded(internalId)

        val count = dao.getScanCountForNetwork(internalId)

        // CALCOLA SEMPRE per le prime 5 scansioni (per dare sùbito una posizione).
        // Poi, per non sovraccaricare il sistema, calcola solo ogni 5 nuove scansioni.
        if (count <= scansThresholdForCompute || count % scansThresholdForCompute == 0) {
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

    fun searchNetworksAdvancedPaged(
        ssid: String, bssid: String, address: String, security: String, limit: Int, offset: Int
    ): Flow<List<WifiNetwork>> = dao.searchNetworksAdvancedPaged(ssid, bssid, address, security, limit, offset)

    fun countNetworksAdvancedFiltered(
        ssid: String, bssid: String, address: String, security: String
    ): Flow<Int> = dao.countNetworksAdvancedFiltered(ssid, bssid, address, security)

    suspend fun hasFilteredNetworkAtOffset(
        ssid: String, bssid: String, address: String, security: String, offset: Int
    ): Boolean = dao.getNetworkIdAtFilteredOffset(ssid, bssid, address, security, offset) != null

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

    suspend fun deleteAllRecords() = dao.deleteAllRecords()
    suspend fun deleteAllSessions() = dao.deleteAllSessions()
    suspend fun deleteAllNetworks() = dao.deleteAllNetworks()

    suspend fun insertRecords(recordList: List<WifiScanRecord>) {
        dao.insertRecords(recordList)
    }

    suspend fun importMergeBundle(
        bundle: com.example.scannerone.io.ExportBundle,
        db: com.example.scannerone.database.AppDatabase
    ) {
        db.withTransaction {
            val networkIdMap = mutableMapOf<Int, Int>()
            bundle.networks?.forEach { importedNetwork ->
                val rowId = dao.insertNetwork(importedNetwork.copy(id = 0))
                val actualId = if (rowId == -1L) {
                    dao.getNetworkIdByBssid(importedNetwork.bssid)
                        ?: error("BSSID ${importedNetwork.bssid}: inserimento ignorato ma ID non trovato")
                } else {
                    rowId.toInt()
                }
                networkIdMap[importedNetwork.id] = actualId
            }

            val sessionIdMap = mutableMapOf<Int, Int>()
            bundle.sessions?.forEach { importedSession ->
                val newId = dao.insertSession(importedSession.copy(id = 0))
                sessionIdMap[importedSession.id] = newId.toInt()
            }

            bundle.records?.chunked(200)?.forEach { chunk ->
                val remapped = chunk.map { record ->
                    record.copy(
                        id = 0,
                        networkId = networkIdMap[record.networkId] ?: 0,
                        sessionId = record.sessionId?.let { sessionIdMap[it] }
                    )
                }
                dao.insertRecords(remapped)
            }
        }
    }

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
