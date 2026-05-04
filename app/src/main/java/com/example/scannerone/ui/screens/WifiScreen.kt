package com.example.scannerone.ui.screens

import android.content.Context
import android.content.Intent
import android.net.wifi.ScanResult
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.scannerone.R
import com.example.scannerone.permissions.PermissionGroup
import com.example.scannerone.permissions.rememberPermissionState
import com.example.scannerone.services.ScanService.WifiForegroundService

@Composable
fun WifiScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Osserva direttamente il StateFlow del servizio — persistente per tutta la vita del processo
    val isWarDrivingContinuo by WifiForegroundService.isRunning.collectAsState()
    val scanResults by WifiForegroundService.lastScanResults.collectAsState()

    val permissionState = rememberPermissionState(
        PermissionGroup.WIFI,
        PermissionGroup.LOCATION
    )

    val permissionStateForeground = rememberPermissionState(
        PermissionGroup.WIFI,
        PermissionGroup.LOCATION,
        PermissionGroup.NOTIFICATION,
        PermissionGroup.ACTIVITY_RECOGNITION
    )

    fun isThrottleEnabled(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
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
            val intent = Intent(context, WifiForegroundService::class.java)
            context.stopService(intent)
            Toast.makeText(context, context.getString(R.string.wifi_toast_wardriving_stopped), Toast.LENGTH_SHORT).show()
        } else {
            if (!isLocationEnabled(context)) {
                Toast.makeText(context, context.getString(R.string.wifi_toast_location_required), Toast.LENGTH_LONG).show()
                val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                context.startActivity(intent)
                return
            }
            permissionStateForeground.runWithPermission {
                val intent = Intent(context, WifiForegroundService::class.java)
                ContextCompat.startForegroundService(context, intent)
                Toast.makeText(context, context.getString(R.string.wifi_toast_wardriving_started), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = { toggleWarDrivingContinuo() },
            enabled = !showThrottleWarning || isWarDrivingContinuo,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isWarDrivingContinuo) Color(0xFF4CAF50)
                else MaterialTheme.colorScheme.secondary
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (isWarDrivingContinuo) {
                    stringResource(R.string.wifi_stop_wardriving)
                } else {
                    stringResource(R.string.wifi_start_wardriving)
                }
            )
        }

        if (showThrottleWarning) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.wifi_throttle_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = stringResource(R.string.wifi_throttle_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        if (!permissionState.allGranted) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.wifi_missing_permissions_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.wifi_missing_permissions_message),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { permissionState.requestPermissions() }) {
                        Text(stringResource(R.string.wifi_grant_permissions))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (scanResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 32.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = stringResource(R.string.wifi_no_networks_last_scan),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val sortedResults = remember(scanResults) {
                scanResults.sortedByDescending { it.level }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sortedResults) { result ->
                    WifiResultItem(result)
                }
            }
        }
    }
}

@Composable
fun WifiResultItem(result: ScanResult) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.SSID.ifEmpty { stringResource(R.string.wifi_hidden_ssid) },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${result.level} dBm",
                    style = MaterialTheme.typography.bodyMedium,
                    color = getSignalColor(result.level)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.wifi_bssid_label, result.BSSID),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = stringResource(
                    R.string.wifi_protocol_label,
                    simplifyCapabilities(context, result.capabilities)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

fun simplifyCapabilities(context: Context, capabilities: String): String {
    return when {
        capabilities.contains("WPA3", ignoreCase = true) -> "WPA3"
        capabilities.contains("WPA2", ignoreCase = true) -> "WPA2"
        capabilities.contains("WPA", ignoreCase = true) -> "WPA"
        capabilities.contains("WEP", ignoreCase = true) -> "WEP"
        capabilities.contains("ESS", ignoreCase = true) && !capabilities.contains("WPA") -> {
            context.getString(R.string.wifi_security_open)
        }
        else -> context.getString(R.string.wifi_security_unknown)
    }
}

fun getSignalColor(level: Int): Color {
    return when {
        level >= -50 -> Color(0xFF4CAF50) // Ottimo
        level >= -60 -> Color(0xFF8BC34A) // Buono
        level >= -70 -> Color(0xFFFFC107) // Discreto
        level >= -80 -> Color(0xFFFF9800) // Scarso
        else -> Color(0xFFF44336) // Molto scarso
    }
}