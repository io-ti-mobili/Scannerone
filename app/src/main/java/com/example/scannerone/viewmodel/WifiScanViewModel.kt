package com.example.scannerone.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.scannerone.database.AppDatabase
import com.example.scannerone.entities.WifiNetwork
import com.example.scannerone.entities.ScanSession
import com.example.scannerone.repository.WifiScanRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.map

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


class WifiScanViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WifiScanRepository(
        AppDatabase.getDatabase(application).wifiScanDao()
    )
    fun deleteNetwork(network: WifiNetwork) {
        viewModelScope.launch {
            repository.deleteNetwork(network)
        }
    }








    // 1. STATO DELLA RICERCA AVANZATA
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

    /**
     * Genera una sessione finta con più scansioni per testare i grafici e la Hall of Fame.
     * @param startTime Tempo di inizio (default 30 min fa)
     */
    fun generateMockSession(startTime: Long = System.currentTimeMillis() - (30 * 60 * 1000)) {
        viewModelScope.launch {
            // Calcoliamo una durata per la sessione (es. 20-45 minuti)
            val durationMs = (20..45).random() * 60 * 1000L
            val endTime = startTime + durationMs
            
            val session = ScanSession(startTime = startTime)
            val sessionId = repository.insertSession(session).toInt()

            val hexChars = "0123456789ABCDEF"
            
            // Pool di SSID basati sulle categorie in UtilsWifi.kt
            val ssidPool = listOf(
                "Vodafone-Station-7812", "TIM-Wi-Fi-Casa", "Fastweb-Home-99", "Iliadbox-A5B2", // ISP
                "McDonalds-Free-WiFi", "Burger-King-Hotspot", "Starbucks-Guest", // FAST_FOOD
                "eduroam", "polimi-guest", "studenti-unimi", "UniMi-Open", // UNIVERSITY
                "iPhone di Marco", "AndroidHotspot456", "Galaxy-S21-Hotspot", // HOTSPOT
                "Netgear-Guest", "TP-LINK_5521", "ASUS-Gaming-2.4G" // OTHER
            )

            // Generiamo 8 reti "fisse" lungo il percorso pescate dal pool
            val mockNetworks = List(8) { i ->
                val bssid = (1..6).joinToString(":") { "${hexChars.random()}${hexChars.random()}" }
                val ssid = ssidPool.random() + "-${(10..99).random()}" // Aggiungiamo un suffisso per varietà
                val cap = if (Math.random() > 0.3) "[WPA2-PSK-CCMP]" else "[OPEN]"
                val freq = if (Math.random() > 0.5) 2412 else 5180
                Triple(bssid, ssid, cap to freq)
            }

            // Simuliamo 15 punti di scansione equamente spalmati nella durata della sessione
            val stepInterval = durationMs / 15
            for (step in 0 until 15) {
                val stepTime = startTime + (step * stepInterval)
                // Spostamento finto (circa 100 metri a step, variabile)
                val lat = 45.4642 + (step * 0.0008) + (Math.random() * 0.0002)
                val lon = 9.1900 + (step * 0.0008) + (Math.random() * 0.0002)

                val visibleCount = (2..4).random()
                val visibleIndices = (mockNetworks.indices).shuffled().take(visibleCount)

                for (idx in visibleIndices) {
                    val netData = mockNetworks[idx]
                    repository.insertScannedNetwork(
                        bssid = netData.first,
                        ssid = netData.second,
                        capabilities = netData.third.first,
                        frequency = netData.third.second,
                        rssi = -(40..85).random(),
                        lat = lat,
                        lon = lon,
                        accuracy = 10f,
                        sessionId = sessionId,
                        forcedTimestamp = stepTime
                    )
                }
            }

            // Chiudiamo la sessione con dati credibili
            val finalSession = session.copy(
                id = sessionId,
                endTime = endTime,
                distanceMetres = 1200.0 + (Math.random() * 500.0) 
            )
            repository.updateSession(finalSession)
        }
    }

    fun generateWeeklyMock() {
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L
        // 3 sessioni negli ultimi 7 giorni
        repeat(3) { i ->
            val pastStart = now - (i * 2 * dayMs) - (Math.random() * dayMs).toLong()
            generateMockSession(pastStart)
        }
    }

    fun generateMonthlyMock() {
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L
        // 6 sessioni negli ultimi 30 giorni
        repeat(6) { i ->
            val pastStart = now - (i * 5 * dayMs) - (Math.random() * 2 * dayMs).toLong()
            generateMockSession(pastStart)
        }
    }

    fun generateYearlyMock() {
        val now = System.currentTimeMillis()
        val monthMs = 30 * 24 * 60 * 60 * 1000L
        // 12 sessioni nell'ultimo anno (una al mese circa)
        repeat(12) { i ->
            val pastStart = now - (i * monthMs) - (Math.random() * 10 * 24 * 60 * 60 * 1000L).toLong()
            generateMockSession(pastStart)
        }
    }


}