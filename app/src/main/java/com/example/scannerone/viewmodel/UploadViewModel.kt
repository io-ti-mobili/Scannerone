package com.example.scannerone.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.scannerone.R
import com.example.scannerone.database.AppDatabase
import com.example.scannerone.repository.SettingsRepository
import com.example.scannerone.services.authApi.AuthClient
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

    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    private val _serverEndpoint = MutableStateFlow(com.example.scannerone.config.AppConfig.BACKEND_URL)
    val serverEndpoint = _serverEndpoint.asStateFlow()

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState = _uploadState.asStateFlow()

    private val _registrationState = MutableStateFlow<RegistrationState>(RegistrationState.Idle)
    val registrationState = _registrationState.asStateFlow()

    init {
        viewModelScope.launch {
            _userUuid.value = settingsRepo.getUserUuid()
            
            // Launch coroutine per username
            launch {
                settingsRepo.usernameFlow.collect {
                    _username.value = it
                }
            }
            
            // Launch coroutine per password
            launch {
                settingsRepo.passwordFlow.collect {
                    _password.value = it
                }
            }

            // Launch coroutine per endpoint
            launch {
                settingsRepo.serverEndpointFlow.collect {
                    _serverEndpoint.value = it
                }
            }
        }
    }

    fun saveUsername(newUsername: String) {
        viewModelScope.launch {
            settingsRepo.saveUsername(newUsername)
        }
    }

    fun saveCredentials(newUsername: String, newUuid: String, newPassword: String, newEndpoint: String) {
        viewModelScope.launch {
            settingsRepo.saveCredentials(newUsername, newUuid, newPassword, newEndpoint)
            _userUuid.value = newUuid // Update memory immediately
            _serverEndpoint.value = newEndpoint
        }
    }

    fun resetRegistrationState() {
        _registrationState.value = RegistrationState.Idle
    }

    fun registerUser(draftEndpoint: String, onSuccess: (String, String) -> Unit) {
        viewModelScope.launch {
            _registrationState.value = RegistrationState.Loading
            try {
                val api = AuthClient.createApi(draftEndpoint)
                val response = api.register()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        _registrationState.value = RegistrationState.Success(
                            getApplication<Application>().getString(R.string.settings_registration_success)
                        )
                        onSuccess(body.uuid, body.password)
                    } else {
                        _registrationState.value = RegistrationState.Error(
                            getApplication<Application>().getString(R.string.settings_registration_error)
                        )
                    }
                } else {
                    _registrationState.value = RegistrationState.Error(
                        getApplication<Application>().getString(R.string.settings_registration_error) + ": ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                _registrationState.value = RegistrationState.Error(
                    getApplication<Application>().getString(R.string.settings_registration_error) + ": ${e.message}"
                )
            }
        }
    }

    fun resetUploadState() {
        _uploadState.value = UploadState.Idle
    }

    fun uploadNetworks() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            if (_username.value.isBlank()) {
                _uploadState.value = UploadState.Error(app.getString(R.string.upload_error_username_required))
                return@launch
            }
            _uploadState.value = UploadState.Loading
            try {
                val networks = db.networkDao().getAllNetworksSync()
                if (networks.isEmpty()) {
                    _uploadState.value = UploadState.Error(app.getString(R.string.upload_error_no_networks))
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
                    password = _password.value,
                    networks = dtoList
                )
                val api = UploadClient.createApi(_serverEndpoint.value)
                val response = api.uploadNetworks(request)
                if (response.isSuccessful) {
                    val body = response.body()
                    _uploadState.value = UploadState.Success(
                        app.getString(
                            R.string.upload_success_summary,
                            body?.processed ?: 0,
                            body?.errors ?: 0
                        )
                    )
                } else {
                    _uploadState.value = UploadState.Error(
                        app.getString(R.string.upload_error_server_code, response.code())
                    )
                }
            } catch (e: Exception) {
                _uploadState.value = UploadState.Error(
                    app.getString(
                        R.string.upload_error_network,
                        e.message ?: app.getString(R.string.common_error_unknown)
                    )
                )
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

sealed class RegistrationState {
    object Idle : RegistrationState()
    object Loading : RegistrationState()
    data class Success(val message: String) : RegistrationState()
    data class Error(val message: String) : RegistrationState()
}
