package com.example.scannerone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.scannerone.ui.AppScaffold
import com.example.scannerone.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var themePreference by remember { mutableStateOf<Boolean?>(null) }


            val isDark = themePreference ?: isSystemInDarkTheme()

            MyApplicationTheme(darkTheme = isDark) {
                AppScaffold(
                    isDark = isDark,
                    onThemeChange = { nuovaScelta -> themePreference = nuovaScelta }
                )
            }
        }
    }
}