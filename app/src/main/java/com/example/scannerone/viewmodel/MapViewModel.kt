package com.example.scannerone.viewmodel


import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.scannerone.database.AppDatabase
import com.example.scannerone.entities.WifiNetwork
import com.example.scannerone.repository.WifiScanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


class MapViewModel(application: Application) : AndroidViewModel(application) {

    //Inizializziamo il nostro Service
    private val repository = WifiScanRepository(
        AppDatabase.getDatabase(application).wifiScanDao()
    )

    // Lo Stato (Il tubo aperto verso il frontend/MapScreen)
    private val _visibleNetworks = MutableStateFlow<List<WifiNetwork>>(emptyList())
    val visibleNetworks = _visibleNetworks.asStateFlow()

    //endpoint: azione chiamata dal frontend quando l'utente muove la mappa, senza caricare subito i dati delle reti
    fun recuperaRetiInZona(north: Double, south: Double, east: Double, west: Double) {
        viewModelScope.launch {
            println("valori: nord=$north, sud=$south, est=$east, ovest=$west")
            // Chiamiamo il Service passando le coordinate
            val networks = repository.getNetworksInBoundingBox(north, south, east, west)

            // Aggiorniamo lo stato. Il MapScreen si ridisegnerà da solo!
            _visibleNetworks.value = networks
        }
    }
}