package com.example.scannerone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.example.scannerone.repository.SettingsRepository
import com.example.scannerone.ui.AppScaffold
import com.example.scannerone.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsRepository = SettingsRepository(applicationContext)

        setContent {
            val themePreference by settingsRepository.themeFlow
                .collectAsState(initial = null)

            val scope = rememberCoroutineScope()
            val isDark = themePreference ?: isSystemInDarkTheme()

            MyApplicationTheme(darkTheme = isDark) {
                AppScaffold(
                    isDark = isDark,
                    themePreference = themePreference,
                    onThemeChange = { nuovaScelta ->
                        scope.launch { settingsRepository.saveTheme(nuovaScelta) }
                    }
                )
            }
        }
    }
}