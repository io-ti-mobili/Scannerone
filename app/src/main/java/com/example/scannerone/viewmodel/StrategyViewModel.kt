package com.example.scannerone.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.scannerone.database.AppDatabase
import com.example.scannerone.repository.NetworkRepository
import com.example.scannerone.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ViewModel responsabile della configurazione del motore matematico (StrategyConfig).
 * Legge/scrive su DataStore tramite SettingsRepository.
 */
class StrategyViewModel(application: Application) : AndroidViewModel(application) {

    private val db           = AppDatabase.getDatabase(application)
    private val networkRepo  = NetworkRepository(db.networkDao(), db.searchDao())
    private val settingsRepo = SettingsRepository(application)

    private val _config = MutableStateFlow(StrategyConfig())
    val config = _config.asStateFlow()

    private val _draftConfig = MutableStateFlow(StrategyConfig())
    val draftConfig = _draftConfig.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = settingsRepo.strategyConfigFlow.first()
            _config.value      = saved
            _draftConfig.value = saved
            networkRepo.config = saved
        }
    }

    /** Aggiorna il draft (non ancora applicato). */
    fun updateDraftConfig(newConfig: StrategyConfig) {
        _draftConfig.value = newConfig
    }

    /** Applica il draft, lo persiste su DataStore e ricalcola le posizioni nel DB. */
    fun applyDraftAndRecalculate() {
        val applied = _draftConfig.value
        _config.value      = applied
        networkRepo.config = applied

        viewModelScope.launch {
            settingsRepo.saveStrategyConfig(applied)
            networkRepo.recalculateAllNetworks()
        }
    }
}
