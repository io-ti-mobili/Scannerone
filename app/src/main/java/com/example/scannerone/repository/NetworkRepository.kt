package com.example.scannerone.repository

import com.example.scannerone.database.NetworkDao
import com.example.scannerone.database.SearchDao
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
import com.example.scannerone.utils.parseBand
import com.example.scannerone.utils.parseSecurityStr
import com.example.scannerone.viewmodel.StrategyConfig
import com.example.scannerone.viewmodel.StrategyType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class NetworkRepository(private val networkDao: NetworkDao, private val searchDao : SearchDao) {
    private val scansThresholdForCompute = 5
    private val maxScansForCompute = 50

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

    /**
     * Fetches the best [maxScansForCompute] records for this network directly from the DB,
     * already sorted by the composite weight (accuracy 80% + recency 20%).
     * No in-memory filtering or sorting needed; the result is always populated
     * as long as the network has at least one scan record.
     */
    private suspend fun recalculateNetwork(networkId: Int) {
        val bestScans = searchDao.getBestScansForNetwork(networkId, maxScansForCompute)

        if (bestScans.isEmpty()) return

        val newPosition = activeStrategy.calculatePosition(bestScans)

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
        val securityStr = parseSecurityStr(capabilities)
        val band = parseBand(frequency)


        val network = WifiNetwork(
            bssid = bssid,
            ssid = ssid,
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

        val count = networkDao.getScanCountForNetwork(internalId)

        if (count <= scansThresholdForCompute || count % scansThresholdForCompute == 0) {
            calculationChannel.trySend(internalId)
        }
    }

    suspend fun deleteNetwork(network: WifiNetwork) {
        networkDao.deleteNetwork(network)
    }
}