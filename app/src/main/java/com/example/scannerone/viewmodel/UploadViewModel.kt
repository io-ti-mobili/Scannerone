package com.example.scannerone.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.scannerone.database.AppDatabase
import com.example.scannerone.repository.SettingsRepository
import com.example.scannerone.services.uploadApi.UploadClient
import com.example.scannerone.services.uploadApi.UploadRequestDto
import com.example.scannerone.services.uploadApi.WifiNetworkUploadDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel responsabile dell'upload delle reti Wi-Fi al server remoto.
 * Gestisce username, UUID utente e lo stato della sincronizzazione.
 */
class UploadViewModel(application: Application) : AndroidViewModel(application) {

    private val db           = AppDatabase.getDatabase(application)
    private val settingsRepo = SettingsRepository(application)

    private val _userUuid = MutableStateFlow("")
    val userUuid = _userUuid.asStateFlow()

    private val _username = MutableStateFlow("")
    val username = _username.asStateFlow()

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState = _uploadState.asStateFlow()

    init {
        viewModelScope.launch {
            _userUuid.value = settingsRepo.getUserUuid()
            settingsRepo.usernameFlow.collect {
                _username.value = it
            }
        }
    }

    fun saveUsername(newUsername: String) {
        viewModelScope.launch {
            settingsRepo.saveUsername(newUsername)
        }
    }

    fun resetUploadState() {
        _uploadState.value = UploadState.Idle
    }

    fun uploadNetworks() {
        viewModelScope.launch {
            if (_username.value.isBlank()) {
                _uploadState.value = UploadState.Error("Inserisci uno username prima di caricare")
                return@launch
            }
            _uploadState.value = UploadState.Loading
            try {
                val networks = db.networkDao().getAllNetworksSync()
                if (networks.isEmpty()) {
                    _uploadState.value = UploadState.Error("Nessuna rete da caricare")
                    return@launch
                }
                val dtoList = networks.map {
                    WifiNetworkUploadDto(
                        bssid = it.bssid,
                        ssid = it.ssid,
                        frequency = it.frequency,
                        realLatitude = it.realLatitude,
                        realLongitude = it.realLongitude,
                        estAccuracy = it.estAccuracy,
                        category = it.category,
                        security = it.security,
                        frequencyBand = it.frequencyBand
                    )
                }
                val request = UploadRequestDto(
                    username = _username.value,
                    uuid = _userUuid.value,
                    networks = dtoList
                )
                val response = UploadClient.api.uploadNetworks(request)
                if (response.isSuccessful) {
                    val body = response.body()
                    _uploadState.value = UploadState.Success(
                        "Upload completato: ${body?.processed ?: 0} processati, ${body?.errors ?: 0} errori"
                    )
                } else {
                    _uploadState.value = UploadState.Error("Errore dal server: ${response.code()}")
                }
            } catch (e: Exception) {
                _uploadState.value = UploadState.Error("Errore di rete: ${e.message}")
            }
        }
    }
}

sealed class UploadState {
    object Idle : UploadState()
    object Loading : UploadState()
    data class Success(val message: String) : UploadState()
    data class Error(val message: String) : UploadState()
}
