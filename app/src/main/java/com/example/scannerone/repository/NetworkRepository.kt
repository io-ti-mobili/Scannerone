package com.example.scannerone.repository

import com.example.scannerone.database.NetworkDao
import com.example.scannerone.entities.WifiNetwork
import com.example.scannerone.entities.WifiScanRecord
import com.example.scannerone.locationCalc.LocationCalcStrategy
import com.example.scannerone.locationCalc.RansacCalcStrategyWrapper
import com.example.scannerone.locationCalc.TrilaterationCalcStrategy
import com.example.scannerone.locationCalc.WeightedCentroidCalcStrategy
import com.example.scannerone.services.WarDrivingService.WarDrivingConfig
import com.example.scannerone.services.nominatimApi.RateLimitedNominatimProxy
import com.example.scannerone.services.nominatimApi.toWifiNetworkFields
import com.example.scannerone.utils.categorizeNetwork
import com.example.scannerone.viewmodel.StrategyConfig
import com.example.scannerone.viewmodel.StrategyType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class NetworkRepository(private val networkDao: NetworkDao) {
    private val scansThresholdForCompute = 5

    companion object {
        const val MAX_RECORDS_PER_NETWORK = 100
        private const val ACCURACY_WEIGHT = 0.8
        private const val RECENCY_WEIGHT = 0.2
    }

    private val maxAcceptedAccuracy = WarDrivingConfig.MIN_ACCEPTABLE_ACCURACY_M

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var config = StrategyConfig()
        set(value) {
            field = value
            updateActiveStrategy()
        }

    private var activeStrategy: LocationCalcStrategy = WeightedCentroidCalcStrategy()

    private val calculationChannel = Channel<Int>(Channel.UNLIMITED)

    init {
        updateActiveStrategy()

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


    suspend fun recalculateAllNetworks() {
        val networkIds = networkDao.getAllNetworkIds()
        for (id in networkIds) {
            recalculateNetwork(id)
        }
    }


    private val maxScansForCompute = 50

    private suspend fun recalculateNetwork(networkId: Int) {
        val history = networkDao.getScansForNetwork(networkId)

        if (history.isEmpty()) return
        var usableScans = history.filter {
            it.scanAccuracy <= maxAcceptedAccuracy
        }

        if (usableScans.isEmpty()) {
            usableScans = history
        }

        val bestScansForCompute = usableScans
            .sortedByDescending { it.timestamp.toFloat() / (it.scanAccuracy + 1f) }
            .take(maxScansForCompute)
        if (bestScansForCompute.isNotEmpty()) {
            val newPosition = activeStrategy.calculatePosition(bestScansForCompute)

            if (newPosition != null) {
                networkDao.updateNetworkLocation(
                    networkId = networkId,
                    lat = newPosition.latitude,
                    lon = newPosition.longitude,
                    acc = newPosition.accuracy
                )

                fetchAndSetNetworkAddress(networkId, newPosition.latitude, newPosition.longitude)
            }
        }
    }

    private suspend fun fetchAndSetNetworkAddress(networkId: Int, lat: Double, lon: Double) {
        try {
            val response = RateLimitedNominatimProxy.reverseGeocode(lat, lon)
            val fields = response.toWifiNetworkFields()

            networkDao.updateNetworkAddressDetails(
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
        val allRecords = networkDao.getScansForNetwork(networkId)
        if (allRecords.size <= MAX_RECORDS_PER_NETWORK) return

        val scored = allRecords.map { it to computeRecordWeight(it, allRecords) }
        val sorted = scored.sortedBy { it.second }
        val toDelete = sorted.take(allRecords.size - MAX_RECORDS_PER_NETWORK)
        val idsToDelete = toDelete.map { it.first.id }

        networkDao.deleteScanRecordsByIds(idsToDelete)
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

        var internalId = networkDao.insertNetwork(network).toInt()
        val isFirst = internalId != -1
        if (internalId == -1) {
            internalId = networkDao.getNetworkIdByBssid(bssid) ?: return
            networkDao.updateNetworkSsid(bssid, ssid)
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
        networkDao.insertScanRecord(record)
        pruneRecordsIfNeeded(internalId)

        val count = networkDao.getScanCountForNetwork(internalId)

        if (count <= scansThresholdForCompute || count % scansThresholdForCompute == 0) {
            calculationChannel.trySend(internalId)
        }
    }

    suspend fun deleteNetwork(network: WifiNetwork) {
        networkDao.deleteNetwork(network)
    }
}
