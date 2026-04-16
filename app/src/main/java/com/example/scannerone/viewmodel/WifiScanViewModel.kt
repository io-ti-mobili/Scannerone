package com.example.scannerone.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.scannerone.database.AppDatabase
import com.example.scannerone.entities.WifiNetwork
import com.example.scannerone.repository.WifiScanRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

 private const val DB_PAGE_SIZE = 10 //modificare in base a quanti elementi si vogliono mostrare nella schermata databeis

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

    private val _config = MutableStateFlow(StrategyConfig())
    val config = _config.asStateFlow()

    private val _draftConfig = MutableStateFlow(StrategyConfig())
    val draftConfig = _draftConfig.asStateFlow()

    // ==========================================================
    // 1. STATO DELLA RICERCA AVANZATA (Ora è DENTRO la classe!)
    // ==========================================================
    private val _searchFilters = MutableStateFlow(SearchFilters())
    val searchFilters = _searchFilters.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage = _currentPage.asStateFlow()

    private val _isLastPageKnown = MutableStateFlow(false)
    val isLastPageKnown = _isLastPageKnown.asStateFlow()

    private val _isPagingBusy = MutableStateFlow(false)
    val isPagingBusy = _isPagingBusy.asStateFlow()

    fun getDbPageSize(): Int {
        return DB_PAGE_SIZE
    }

    fun applyFilters(address: String, ssid: String, bssid: String, security: String) {
        _searchFilters.value = SearchFilters(ssid.trim(), bssid.trim(), address.trim(), security.trim())
        _currentPage.value = 0
        _isLastPageKnown.value = false
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
    val networks = combine(_searchFilters, _currentPage) { filters, page ->
        Pair(filters, page)
    }
        .flatMapLatest { (filters, page) ->
            repository.searchNetworksAdvancedPaged(
                ssid = filters.ssid,
                bssid = filters.bssid,
                address = filters.address,
                security = filters.security,
                limit = DB_PAGE_SIZE,
                offset = page * DB_PAGE_SIZE
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList<WifiNetwork>()
        )

    val hasPreviousPage = _currentPage
        .map { it > 0 }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val canGoNextPage = combine(networks, _isLastPageKnown, _isPagingBusy) { list, isLastKnown, busy ->
        !busy && !isLastKnown && list.size == DB_PAGE_SIZE
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalFilteredNetworks = _searchFilters
        .flatMapLatest { filters ->
            repository.countNetworksAdvancedFiltered(
                ssid = filters.ssid,
                bssid = filters.bssid,
                address = filters.address,
                security = filters.security
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
    // ==========================================================

    fun goToPreviousPage() {
        if (_isPagingBusy.value) return
        if (_currentPage.value == 0) return
        _currentPage.value -= 1
        _isLastPageKnown.value = false
    }

    fun goToNextPage() {
        if (_isPagingBusy.value) return
        val currentFilters = _searchFilters.value
        val nextPage = _currentPage.value + 1
        val nextOffset = nextPage * DB_PAGE_SIZE

        viewModelScope.launch {
            _isPagingBusy.value = true
            val hasData = withContext(Dispatchers.IO) {
                repository.hasFilteredNetworkAtOffset(
                    ssid = currentFilters.ssid,
                    bssid = currentFilters.bssid,
                    address = currentFilters.address,
                    security = currentFilters.security,
                    offset = nextOffset
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
            _isLastPageKnown.value = false
        }
    }

    fun deleteNetwork(network: WifiNetwork) {
        viewModelScope.launch {
            repository.deleteNetwork(network)
            _isLastPageKnown.value = false
        }
    }
}