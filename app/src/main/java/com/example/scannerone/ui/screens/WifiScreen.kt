package com.example.scannerone.ui.screens

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.scannerone.permissions.PermissionGroup
import com.example.scannerone.permissions.rememberPermissionState
import com.example.scannerone.services.ScanService.WifiForegroundService

@Composable
fun WifiScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // WarDriving continuo (foreground service)
    var isWarDrivingContinuo by rememberSaveable { mutableStateOf(false) }

    val permissionState = rememberPermissionState(
        PermissionGroup.WIFI,
        PermissionGroup.LOCATION
    )

    // Permessi extra per il wardriving continuo (include NOTIFICATION per il foreground service)
    val permissionStateForeground = rememberPermissionState(
        PermissionGroup.WIFI,
        PermissionGroup.LOCATION,
        PermissionGroup.NOTIFICATION
    )

    fun isThrottleEnabled(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            wifiManager?.isScanThrottleEnabled == true
        } else {
            false
        }
    }

    val showThrottleWarning = remember { isThrottleEnabled(context) }

    fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        return locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true ||
                locationManager?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
    }

    fun toggleWarDrivingContinuo() {
        if (isWarDrivingContinuo) {
            // Ferma il servizio
            val intent = Intent(context, WifiForegroundService::class.java)
            context.stopService(intent)
            isWarDrivingContinuo = false
            Toast.makeText(context, "WarDriving continuo disattivato", Toast.LENGTH_SHORT).show()
        } else {
            // Verifica geolocalizzazione attiva
            if (!isLocationEnabled(context)) {
                Toast.makeText(context, "Per il wardriving serve la geolocalizzazione attiva.", Toast.LENGTH_LONG).show()
                val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                context.startActivity(intent)
                return
            }
            // Avvia il servizio (i permessi vengono verificati da runWithPermission)
            permissionStateForeground.runWithPermission {
                val intent = Intent(context, WifiForegroundService::class.java)
                ContextCompat.startForegroundService(context, intent)
                isWarDrivingContinuo = true
                Toast.makeText(context, "WarDriving continuo attivato", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!permissionState.allGranted) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "⚠ Permessi mancanti",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Premi il pulsante per concedere i permessi necessari alla scansione.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { permissionState.requestPermissions() }) {
                        Text("Concedi permessi")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
                if (showThrottleWarning) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Limitazione scansioni attiva (Wi-Fi Throttling)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Android limita le scansioni frequenti. Per disabilitarlo:\nOpzioni Sviluppatore > 'Limitazione ricerca reti Wi-Fi' (OFF).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Wi-Fi Scanner",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ── Bottone WarDriving Continuo (Foreground Service) ──
        Button(
            onClick = { toggleWarDrivingContinuo() },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isWarDrivingContinuo) Color(0xFF4CAF50)
                else MaterialTheme.colorScheme.secondary
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (isWarDrivingContinuo) "🛑 Ferma WarDriving Continuo"
                else "🚀 Avvia WarDriving Continuo"
            )
        }

        // Errore
        errorMessage?.let { err ->
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "⚠ $err",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}