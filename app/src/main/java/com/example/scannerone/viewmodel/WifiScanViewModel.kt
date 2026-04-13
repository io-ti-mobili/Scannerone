package com.example.scannerone.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.scannerone.database.AppDatabase
import com.example.scannerone.entities.WifiNetwork
import com.example.scannerone.repository.WifiScanRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.scannerone.utils.NetworkCategory
import com.example.scannerone.utils.categorizeNetwork

enum class StrategyType { CENTROID, TRILATERATION }

// I modelli dei dati possono stare fuori dalla classe
data class SearchFilters(
    val ssid: String = "",
    val bssid: String = "",
    val address: String = "",
    val security: String = "Tutte"
)

data class StrategyConfig(
    val baseStrategyType: StrategyType = StrategyType.CENTROID,
    val useRansac: Boolean = false,
    val useGpsWeight: Boolean = false
)

data class SessionStats(
    val uniqueNetworks: Int = 0,
    val totalScans: Int = 0,
    val avgRssi: Int = 0
)

class WifiScanViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WifiScanRepository(
        AppDatabase.getDatabase(application).wifiScanDao()
    )
    fun deleteNetwork(network: WifiNetwork) {
        viewModelScope.launch {
            repository.deleteNetwork(network)
        }
    }








    // 1. Totale Reti
    val totalNetworksCount = repository.getTotalNetworksCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // 2. Totale Scansioni
    val totalScansCount = repository.getTotalScansCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)



    val allSessions = repository.getAllSessions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList<com.example.scannerone.entities.ScanSession>()
        )



    @OptIn(ExperimentalCoroutinesApi::class)
    val lastSessionStats = allSessions.flatMapLatest { sessions ->
        val lastSession = sessions.firstOrNull()
        if (lastSession == null) {
            kotlinx.coroutines.flow.flowOf(SessionStats())
        } else {
            repository.getScanRecordsForSession(lastSession.id).map { scans ->
                val uniqueBssids = scans.map { it.networkId }.distinct().size
                val avgRssi = if (scans.isNotEmpty()) scans.map { it.rssi }.average().toInt() else 0
                SessionStats(uniqueNetworks = uniqueBssids, totalScans = scans.size, avgRssi = avgRssi)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SessionStats())
    private val _config = MutableStateFlow(StrategyConfig())
    val config = _config.asStateFlow()

    private val _draftConfig = MutableStateFlow(StrategyConfig())
    val draftConfig = _draftConfig.asStateFlow()

    // ==========================================================
    // 1. STATO DELLA RICERCA AVANZATA (Ora è DENTRO la classe!)
    // ==========================================================
    private val _searchFilters = MutableStateFlow(SearchFilters())
    val searchFilters = _searchFilters.asStateFlow()

    fun applyFilters(address: String, ssid: String, bssid: String, security: String) {
        _searchFilters.value = SearchFilters(ssid, bssid, address, security)
    }
    // ==========================================================

    fun updateDraftConfig(newConfig: StrategyConfig) {
        _draftConfig.value = newConfig
    }

    fun applyDraftAndRecalculate() {
        val applied = _draftConfig.value
        _config.value = applied
        repository.config = applied

        viewModelScope.launch {
            repository.recalculateAllNetworks()
        }
    }

    // ==========================================================
    // 2. NETWORKS REAGISCE AI FILTRI AVANZATI
    // ==========================================================
    @OptIn(ExperimentalCoroutinesApi::class)
    val networks = _searchFilters
        .flatMapLatest { filters ->
            // Passiamo i filtri al Repository, che a sua volta li passa al DAO
            repository.searchNetworksAdvanced(
                ssid = filters.ssid,
                bssid = filters.bssid,
                address = filters.address,
                security = filters.security
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList<WifiNetwork>()
        )
    // ==========================================================

    fun insertScannedNetwork(
        bssid: String,
        ssid: String,
        capabilities: String,
        frequency: Int,
        rssi: Int,
        lat: Double,
        lon: Double,
        accuracy: Float
    ) {
        viewModelScope.launch {
            repository.insertScannedNetwork(bssid, ssid, capabilities, frequency, rssi, lat, lon, accuracy)
        }
    }

    // 2. Sessione attualmente selezionata nel menu a tendina (null = Tutte le sessioni)
    private val _selectedSessionId = MutableStateFlow<Int?>(null)
    val selectedSessionId = _selectedSessionId.asStateFlow()

    fun selectSession(sessionId: Int?) {
        _selectedSessionId.value = sessionId
    }

    // 3. Reti trovate nella sessione selezionata (Per Statistiche e Torta)
    @OptIn(ExperimentalCoroutinesApi::class)
    val sessionNetworks = _selectedSessionId
        .flatMapLatest { id -> repository.getNetworksForSession(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 4. Calcolo Grafico a Torta (reale)
    val categoryStats = sessionNetworks.map { networkList ->
        var isp = 0f; var fastFood = 0f; var uni = 0f; var hotspot = 0f; var other = 0f
        networkList.forEach { net ->
            when(categorizeNetwork(net.ssid)) {
                NetworkCategory.ISP -> isp += 1f
                NetworkCategory.FAST_FOOD -> fastFood += 1f
                NetworkCategory.UNIVERSITY -> uni += 1f
                NetworkCategory.HOTSPOT -> hotspot += 1f
                NetworkCategory.OTHER -> other += 1f
            }
        }
        mapOf(
            "ISP (Tim, Vodafone...)" to isp,
            "Fast Food" to fastFood,
            "Università" to uni,
            "Hotspot Personali" to hotspot,
            "Altro" to other
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // 5. Calcolo Grafico a Linea (Punti scansionati nel tempo per questa sessione)
    @OptIn(ExperimentalCoroutinesApi::class)
    val trendStats = _selectedSessionId
        .flatMapLatest { id -> repository.getScanRecordsForSession(id) }
        .map { records ->
            if (records.isEmpty()) return@map emptyList<Pair<String, Int>>()

            // Ordiniamo per tempo
            val sorted = records.sortedBy { it.timestamp }
            val startTime = sorted.first().timestamp
            val endTime = sorted.last().timestamp
            val totalDuration = (endTime - startTime).coerceAtLeast(1L)

            // Creiamo 5 punti temporali lungo la durata della sessione
            val numSteps = 5
            val stepDuration = totalDuration / numSteps

            val uniqueNetworks = mutableSetOf<Int>()
            val trend = mutableListOf<Pair<String, Int>>()
            var recordIndex = 0

            for (i in 0..numSteps) {
                val currentPointTime = startTime + (i * stepDuration)

                // Aggiungiamo tutte le reti viste fino a questo punto temporale
                while (recordIndex < sorted.size && sorted[recordIndex].timestamp <= currentPointTime) {
                    uniqueNetworks.add(sorted[recordIndex].networkId)
                    recordIndex++
                }

                // Formattiamo l'orario (Es: "14:30:00")
                val timeString = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(currentPointTime))
                trend.add(Pair(timeString, uniqueNetworks.size))
            }
            trend
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun formatTimestamp(time: Long): String {
        return SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(time))
    }
}