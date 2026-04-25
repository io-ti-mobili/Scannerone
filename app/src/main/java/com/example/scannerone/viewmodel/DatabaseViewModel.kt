package com.example.scannerone.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.scannerone.database.AppDatabase
import com.example.scannerone.entities.ScanSession
import com.example.scannerone.entities.WifiNetwork
import com.example.scannerone.repository.NetworkRepository
import com.example.scannerone.repository.SearchRepository
import com.example.scannerone.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DB_PAGE_SIZE = 10

/**
 * ViewModel responsabile della schermata Database:
 * - ricerca/filtri/paginazione reti
 * - eliminazione reti
 * - conteggio scansioni per rete
 * - generazione dati mock per test
 */
class DatabaseViewModel(application: Application) : AndroidViewModel(application) {

    private val db             = AppDatabase.getDatabase(application)
    private val networkRepo    = NetworkRepository(db.networkDao(), db.searchDao())
    private val searchRepo     = SearchRepository(db.searchDao())
    private val sessionRepo    = SessionRepository(db.sessionDao())

    // ---- Filtri e paginazione ----

    private val _searchFilters = MutableStateFlow(SearchFilters())
    val searchFilters = _searchFilters.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage = _currentPage.asStateFlow()

    private val _isLastPageKnown = MutableStateFlow(false)

    private val _isPagingBusy = MutableStateFlow(false)
    val isPagingBusy = _isPagingBusy.asStateFlow()

    fun getDbPageSize(): Int = DB_PAGE_SIZE

    fun applyFilters(address: String, ssid: String, bssid: String, security: String) {
        _searchFilters.value = SearchFilters(ssid.trim(), bssid.trim(), address.trim(), security.trim())
        _currentPage.value = 0
        _isLastPageKnown.value = false
    }

    // ---- Dati reti paginati ----

    @OptIn(ExperimentalCoroutinesApi::class)
    val networks = combine(_searchFilters, _currentPage) { filters, page ->
        filters to page
    }.flatMapLatest { pair: Pair<SearchFilters, Int> ->
        val (filters, page) = pair
        searchRepo.searchNetworksAdvancedPaged(
            ssid     = filters.ssid,
            bssid    = filters.bssid,
            address  = filters.address,
            security = filters.security,
            limit    = DB_PAGE_SIZE,
            offset   = page * DB_PAGE_SIZE
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList<WifiNetwork>()
    )

    val hasPreviousPage = _currentPage
        .map { it > 0 }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val canGoNextPage = combine(networks, _isLastPageKnown, _isPagingBusy) { list, isLastKnown, busy ->
        !busy && !isLastKnown && list.size == DB_PAGE_SIZE
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalFilteredNetworks = _searchFilters
        .flatMapLatest { filters: SearchFilters ->
            searchRepo.countNetworksAdvancedFiltered(
                ssid     = filters.ssid,
                bssid    = filters.bssid,
                address  = filters.address,
                security = filters.security
            )
        }.stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    // ---- Navigazione pagine ----

    fun goToPreviousPage() {
        if (_isPagingBusy.value) return
        if (_currentPage.value == 0) return
        _currentPage.value -= 1
        _isLastPageKnown.value = false
    }

    fun goToNextPage() {
        if (_isPagingBusy.value) return
        val currentFilters = _searchFilters.value
        val nextPage   = _currentPage.value + 1
        val nextOffset = nextPage * DB_PAGE_SIZE

        viewModelScope.launch {
            _isPagingBusy.value = true
            val hasData = withContext(Dispatchers.IO) {
                searchRepo.hasFilteredNetworkAtOffset(
                    ssid     = currentFilters.ssid,
                    bssid    = currentFilters.bssid,
                    address  = currentFilters.address,
                    security = currentFilters.security,
                    offset   = nextOffset
                )
            }
            if (hasData) {
                _currentPage.value = nextPage
            } else {
                _isLastPageKnown.value = true
            }
            _isPagingBusy.value = false
        }
    }

    // ---- Operazioni su reti ----

    fun deleteNetwork(network: WifiNetwork) {
        viewModelScope.launch {
            networkRepo.deleteNetwork(network)
            _isLastPageKnown.value = false
        }
    }

    suspend fun getNetworkScanCount(networkId: Int): Int =
        withContext(Dispatchers.IO) {
            db.networkDao().getScanCountForNetwork(networkId)
        }

    // ---- Mock data per test ----

    /**
     * Genera una sessione finta con più scansioni per testare grafici e Hall of Fame.
     * @param startTime Timestamp di inizio (default: 30 min fa)
     */
    fun generateMockSession(startTime: Long = System.currentTimeMillis() - (30 * 60 * 1000)) {
        viewModelScope.launch {
            val durationMs = (20..45).random() * 60 * 1000L
            val endTime    = startTime + durationMs

            val session   = ScanSession(startTime = startTime)
            val sessionId = sessionRepo.insertSession(session).toInt()

            val hexChars = "0123456789ABCDEF"
            val ssidPool = listOf(
                "Vodafone-Station-7812", "TIM-Wi-Fi-Casa", "Fastweb-Home-99", "Iliadbox-A5B2",
                "McDonalds-Free-WiFi", "Burger-King-Hotspot", "Starbucks-Guest",
                "eduroam", "polimi-guest", "studenti-unimi", "UniMi-Open",
                "iPhone di Marco", "AndroidHotspot456", "Galaxy-S21-Hotspot",
                "Netgear-Guest", "TP-LINK_5521", "ASUS-Gaming-2.4G"
            )

            val mockNetworks = List(8) {
                val bssid = (1..6).joinToString(":") { "${hexChars.random()}${hexChars.random()}" }
                val ssid  = ssidPool.random() + "-${(10..99).random()}"
                val cap   = if (Math.random() > 0.3) "[WPA2-PSK-CCMP]" else "[OPEN]"
                val freq  = if (Math.random() > 0.5) 2412 else 5180
                Triple(bssid, ssid, cap to freq)
            }

            val stepInterval = durationMs / 15
            for (step in 0 until 15) {
                val stepTime = startTime + (step * stepInterval)
                val lat = 45.4642 + (step * 0.0008) + (Math.random() * 0.0002)
                val lon = 9.1900  + (step * 0.0008) + (Math.random() * 0.0002)

                val visibleIndices = (mockNetworks.indices).shuffled().take((2..4).random())
                for (idx in visibleIndices) {
                    val netData = mockNetworks[idx]
                    networkRepo.insertScannedNetwork(
                        bssid           = netData.first,
                        ssid            = netData.second,
                        capabilities    = netData.third.first,
                        frequency       = netData.third.second,
                        rssi            = -(40..85).random(),
                        lat             = lat,
                        lon             = lon,
                        accuracy        = 10f,
                        sessionId       = sessionId,
                        forcedTimestamp = stepTime
                    )
                }
            }

            sessionRepo.updateSession(
                session.copy(
                    id             = sessionId,
                    endTime        = endTime,
                    distanceMetres = 1200.0 + (Math.random() * 500.0)
                )
            )
        }
    }

    fun generateWeeklyMock() {
        val now   = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L
        repeat(3) { i ->
            generateMockSession(now - (i * 2 * dayMs) - (Math.random() * dayMs).toLong())
        }
    }

    fun generateMonthlyMock() {
        val now   = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L
        repeat(6) { i ->
            generateMockSession(now - (i * 5 * dayMs) - (Math.random() * 2 * dayMs).toLong())
        }
    }

    fun generateYearlyMock() {
        val now     = System.currentTimeMillis()
        val monthMs = 30 * 24 * 60 * 60 * 1000L
        repeat(12) { i ->
            generateMockSession(now - (i * monthMs) - (Math.random() * 10 * 24 * 60 * 60 * 1000L).toLong())
        }
    }
}
