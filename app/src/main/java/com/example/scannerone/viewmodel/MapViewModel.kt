package com.example.scannerone.viewmodel


import android.app.Application
import android.location.Address
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.scannerone.database.AppDatabase
import com.example.scannerone.entities.WifiNetwork
import com.example.scannerone.repository.WifiScanRepository
import com.example.scannerone.services.nominatimApi.NominatimClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.bonuspack.location.GeocoderNominatim
import org.osmdroid.util.GeoPoint
import java.util.Locale


class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WifiScanRepository(
        AppDatabase.getDatabase(application).wifiScanDao()
    )

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
    // Evento per muovere la telecamera
    private val _moveToLocation = MutableSharedFlow<GeoPoint>()
    val moveToLocation = _moveToLocation.asSharedFlow()

    // Stato per la lista dei suggerimenti a tendina
    private val _searchSuggestions = MutableStateFlow<List<Address>>(emptyList())
    val searchSuggestions = _searchSuggestions.asStateFlow()

    // Job per gestire il ritardo (Debounce)
    private var searchJob: Job? = null

    fun onSearchQueryChanged(query: String) {
        // Cancelliamo la ricerca precedente se l'utente sta ancora digitando
        searchJob?.cancel()

        // Se il testo è troppo corto, svuotiamo i suggerimenti
        if (query.length < 3) {
            _searchSuggestions.value = emptyList()
            return
        }

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500) // DEBOUNCE: Aspettiamo mezzo secondo di inattività
            try {

                val geocoder = GeocoderNominatim(Locale.getDefault(), "ScannerOneApp/1.0")
                val g = NominatimClient


                // Chiediamo un massimo di 5 risultati
                val results = geocoder.getFromLocationName(query, 5)

                if (results != null) {
                    _searchSuggestions.value = results
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _searchSuggestions.value = emptyList()
            }
        }
    }

    // Chiamato quando l'utente clicca su un suggerimento della tendina
    fun selectLocation(address: Address) {
        viewModelScope.launch {
            val point = GeoPoint(address.latitude, address.longitude)
            println(point.toString())
            _moveToLocation.emit(point)
            _searchSuggestions.value = emptyList() // Chiudiamo la tendina
        }
    }
}