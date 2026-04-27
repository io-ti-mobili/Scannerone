package com.example.scannerone.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.scannerone.utils.LocaleUtils
import com.example.scannerone.viewmodel.StrategyConfig
import com.example.scannerone.viewmodel.StrategyType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Singleton DataStore legato al Context dell'app
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val KEY_STRATEGY_TYPE   = stringPreferencesKey("strategy_type")
        private val KEY_USE_RANSAC      = booleanPreferencesKey("use_ransac")
        private val KEY_USE_GPS_WEIGHT  = booleanPreferencesKey("use_gps_weight")
        private val KEY_THEME           = stringPreferencesKey("theme_preference") // "LIGHT" | "DARK" | "SYSTEM"
        private val KEY_APP_LANGUAGE    = stringPreferencesKey("app_language") // "it" | "en"
        private val KEY_USER_UUID       = stringPreferencesKey("user_uuid")
        private val KEY_USERNAME        = stringPreferencesKey("username")
        private val KEY_PASSWORD        = stringPreferencesKey("password")
        private val KEY_SERVER_ENDPOINT = stringPreferencesKey("server_endpoint")
    }

    private fun normalizedLanguageOrNull(languageCode: String?): String? {
        return LocaleUtils.normalizeLanguage(languageCode)
    }

    private fun resolveSystemLanguage(): String {
        return LocaleUtils.systemLanguageOrDefault(context)
    }

    // ---- StrategyConfig ----

    val strategyConfigFlow: Flow<StrategyConfig> = context.settingsDataStore.data.map { prefs ->
        StrategyConfig(
            baseStrategyType = when (prefs[KEY_STRATEGY_TYPE]) {
                "TRILATERATION" -> StrategyType.TRILATERATION
                else            -> StrategyType.CENTROID
            },
            useRansac     = prefs[KEY_USE_RANSAC]     ?: false,
            useGpsWeight  = prefs[KEY_USE_GPS_WEIGHT] ?: false
        )
    }

    suspend fun saveStrategyConfig(config: StrategyConfig) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_STRATEGY_TYPE]  = config.baseStrategyType.name
            prefs[KEY_USE_RANSAC]     = config.useRansac
            prefs[KEY_USE_GPS_WEIGHT] = config.useGpsWeight
        }
    }

    // ---- Tema ----

    /** Emette: null = segui sistema, true = scuro, false = chiaro */
    val themeFlow: Flow<Boolean?> = context.settingsDataStore.data.map { prefs ->
        when (prefs[KEY_THEME]) {
            "DARK"  -> true
            "LIGHT" -> false
            else    -> null  // "SYSTEM" o non impostato
        }
    }

    suspend fun saveTheme(isDark: Boolean?) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_THEME] = when (isDark) {
                true  -> "DARK"
                false -> "LIGHT"
                null  -> "SYSTEM"
            }
        }
    }

    // ---- Lingua App ----

    val appLanguageFlow: Flow<String> = context.settingsDataStore.data.map { prefs ->
        normalizedLanguageOrNull(prefs[KEY_APP_LANGUAGE]) ?: resolveSystemLanguage()
    }

    suspend fun ensureLanguageInitialized() {
        context.settingsDataStore.edit { prefs ->
            if (normalizedLanguageOrNull(prefs[KEY_APP_LANGUAGE]) == null) {
                prefs[KEY_APP_LANGUAGE] = resolveSystemLanguage()
            }
        }
    }

    suspend fun saveAppLanguage(languageCode: String) {
        val safeLanguage = normalizedLanguageOrNull(languageCode) ?: LocaleUtils.DEFAULT_LANGUAGE
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_APP_LANGUAGE] = safeLanguage
        }
    }

    suspend fun getStoredLanguageOrNull(): String? {
        val prefs = context.settingsDataStore.data.first()
        return normalizedLanguageOrNull(prefs[KEY_APP_LANGUAGE])
    }

    suspend fun getCurrentLanguage(): String {
        return appLanguageFlow.first()
    }

    // ---- UUID ----

    suspend fun getUserUuid(): String {
        var uuid = ""
        context.settingsDataStore.edit { prefs ->
            val current = prefs[KEY_USER_UUID]
            if (current == null) {
                uuid = java.util.UUID.randomUUID().toString()
                prefs[KEY_USER_UUID] = uuid
            } else {
                uuid = current
            }
        }
        return uuid
    }

    // ---- Username ----

    val usernameFlow: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_USERNAME] ?: ""
    }

    suspend fun saveUsername(username: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_USERNAME] = username
        }
    }

    // ---- Password ----

    val passwordFlow: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_PASSWORD] ?: ""
    }

    suspend fun savePassword(password: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_PASSWORD] = password
        }
    }

    // ---- Server Endpoint ----

    val serverEndpointFlow: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_SERVER_ENDPOINT] ?: com.example.scannerone.config.AppConfig.BACKEND_URL
    }

    suspend fun saveServerEndpoint(endpoint: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_SERVER_ENDPOINT] = endpoint
        }
    }

    // ---- Save All Credentials ----
    suspend fun saveCredentials(username: String, uuid: String, password: String, endpoint: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_USERNAME] = username
            prefs[KEY_USER_UUID] = uuid
            prefs[KEY_PASSWORD] = password
            prefs[KEY_SERVER_ENDPOINT] = endpoint
        }
    }
}
