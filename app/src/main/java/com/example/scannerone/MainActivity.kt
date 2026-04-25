package com.example.scannerone

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.lifecycleScope
import com.example.scannerone.repository.SettingsRepository
import com.example.scannerone.ui.AppScaffold
import com.example.scannerone.ui.theme.MyApplicationTheme
import com.example.scannerone.utils.LocaleUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val settingsRepository = SettingsRepository(newBase.applicationContext)
        val storedLanguage = runBlocking { settingsRepository.getStoredLanguageOrNull() }
        val resolvedLanguage = LocaleUtils.resolveAppLanguage(newBase, storedLanguage)
        val localizedContext = LocaleUtils.applyLocale(newBase, resolvedLanguage)
        super.attachBaseContext(localizedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsRepository = SettingsRepository(applicationContext)
        lifecycleScope.launch {
            settingsRepository.ensureLanguageInitialized()
        }

        setContent {
            val themePreference by settingsRepository.themeFlow
                .collectAsState(initial = null)
            val appLanguage by settingsRepository.appLanguageFlow
                .collectAsState(initial = LocaleUtils.systemLanguageOrDefault(applicationContext))

            val scope = rememberCoroutineScope()
            val isDark = themePreference ?: isSystemInDarkTheme()

            MyApplicationTheme(darkTheme = isDark) {
                AppScaffold(
                    isDark = isDark,
                    themePreference = themePreference,
                    appLanguage = appLanguage,
                    onThemeChange = { nuovaScelta ->
                        scope.launch { settingsRepository.saveTheme(nuovaScelta) }
                    },
                    onLanguageChange = { languageCode ->
                        if (languageCode != appLanguage) {
                            scope.launch {
                                settingsRepository.saveAppLanguage(languageCode)
                                LocaleUtils.updateResources(applicationContext, languageCode)
                                recreate()
                            }
                        }
                    }
                )
            }
        }
    }
}