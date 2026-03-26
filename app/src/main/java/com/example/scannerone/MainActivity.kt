package com.example.scannerone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.scannerone.Services.WifiForegroundService
import com.example.scannerone.ui.AppScaffold
import com.example.scannerone.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false

        if (locationGranted) {
            avviaServizioBackground()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val permessiDaChiedere = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permessiDaChiedere.add(Manifest.permission.POST_NOTIFICATIONS)
        }


        val tuttiGarantiti = permessiDaChiedere.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (tuttiGarantiti) {
            avviaServizioBackground()
        } else {
            requestPermissionsLauncher.launch(permessiDaChiedere.toTypedArray())
        }

        setContent {
            MyApplicationTheme {
                AppScaffold()
            }
        }
    }

    private fun avviaServizioBackground() {
        val serviceIntent = Intent(this, WifiForegroundService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }
}