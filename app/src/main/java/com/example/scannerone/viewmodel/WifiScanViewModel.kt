package com.example.scannerone.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.scannerone.database.AppDatabase
import com.example.scannerone.repository.WifiScanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class StrategyType { CENTROID, TRILATERATION }

data class StrategyConfig(
    val baseStrategyType: StrategyType = StrategyType.CENTROID,
    val useRansac: Boolean = false,
    val useGpsWeight: Boolean = false
)

class WifiScanViewModel(application: Application) : AndroidViewModel(application) {
    
    // Inizializziamo il Repository passandogli l'istanza unica del DAO
    private val repository = WifiScanRepository(
        AppDatabase.getDatabase(application).wifiScanDao()
    )

    private val _config = MutableStateFlow(StrategyConfig())
    val config = _config.asStateFlow()

    private val _draftConfig = MutableStateFlow(StrategyConfig())
    val draftConfig = _draftConfig.asStateFlow()

    fun updateDraftConfig(newConfig: StrategyConfig) {
        _draftConfig.value = newConfig
    }

    fun applyDraftAndRecalculate() {
        val applied = _draftConfig.value
        _config.value = applied
        repository.config = applied
        
        // Lancio ricalcolo di massa SOLO dopo pressione del tasto Applica
        viewModelScope.launch {
            repository.recalculateAllNetworks()
        }
    }

    // Convertiamo il Flow crudo del Repository in uno StateFlow per Jetpack Compose
    val networks = repository.networks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

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
        // Il ViewModel si limita a inoltrare la richiesta allo strato sottostante. Logica pulitissima!
        viewModelScope.launch {
            repository.insertScannedNetwork(bssid, ssid, capabilities, frequency, rssi, lat, lon, accuracy)
        }
    }
}
