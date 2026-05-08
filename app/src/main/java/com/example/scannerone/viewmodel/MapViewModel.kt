package com.example.scannerone.viewmodel


import android.app.Application
import android.location.Address
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.scannerone.database.AppDatabase
import com.example.scannerone.entities.WifiNetwork
import com.example.scannerone.repository.MapRepository
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
import kotlin.math.max
import kotlin.math.min


class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MapRepository(
        AppDatabase.getDatabase(application).mapDao()
    )

    private val _visibleNetworks = MutableStateFlow<List<WifiNetwork>>(emptyList())
    val visibleNetworks = _visibleNetworks.asStateFlow()

    private var fetchJob: Job? = null
    private var lastRequestId: Long = 0

    private val fetchMarginFraction = 0.03
    private val fetchMarginMinDegrees = 0.0005
    private val fetchMarginMaxDegrees = 0.01


    //endpoint: azione chiamata dal frontend quando l'utente muove la mappa, senza caricare subito i dati delle reti
    fun recuperaRetiInZona(north: Double, south: Double, east: Double, west: Double) {
        fetchJob?.cancel()
        val requestId = ++lastRequestId
        val northNorm = max(north, south)
        val southNorm = min(north, south)
        val eastNorm = max(east, west)
        val westNorm = min(east, west)

        val latSpan = (northNorm - southNorm).coerceAtLeast(0.0)
        val lonSpan = (eastNorm - westNorm).coerceAtLeast(0.0)

        val marginLat = (latSpan * fetchMarginFraction)
            .coerceIn(fetchMarginMinDegrees, fetchMarginMaxDegrees)
        val marginLon = (lonSpan * fetchMarginFraction)
            .coerceIn(fetchMarginMinDegrees, fetchMarginMaxDegrees)

        val northExpanded = (northNorm + marginLat).coerceAtMost(90.0)
        val southExpanded = (southNorm - marginLat).coerceAtLeast(-90.0)
        val eastExpanded = (eastNorm + marginLon).coerceAtMost(180.0)
        val westExpanded = (westNorm - marginLon).coerceAtLeast(-180.0)

        fetchJob = viewModelScope.launch {
            println("valori: nord=$northExpanded, sud=$southExpanded, est=$eastExpanded, ovest=$westExpanded")
            val networks = kotlinx.coroutines.withContext(Dispatchers.IO) {
                repository.getNetworksInBoundingBox(northExpanded, southExpanded, eastExpanded, westExpanded)
            }

            // Evita che una risposta vecchia sovrascriva quella piu recente.
            if (requestId == lastRequestId) {
                _visibleNetworks.value = networks
            }
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