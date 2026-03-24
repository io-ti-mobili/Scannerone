package com.example.scannerone.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.scannerone.database.AppDatabase
import com.example.scannerone.entities.WifiScan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WifiScanViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).wifiScanDao()

    private val _scansioni = MutableStateFlow<List<WifiScan>>(emptyList())
    val scansioni: StateFlow<List<WifiScan>> = _scansioni.asStateFlow()

    init {
        // La lettura è reattiva grazie a Flow: ogni volta che il DB cambia, la UI si aggiorna gratis in tempo reale!
        viewModelScope.launch {
            dao.getAllScans().collect { scans ->
                _scansioni.value = scans
            }
        }
    }

    fun insertScan(scan: WifiScan) {
        viewModelScope.launch {
            dao.insertScan(scan)
        }
    }
}
