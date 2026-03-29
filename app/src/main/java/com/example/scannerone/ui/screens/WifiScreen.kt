package com.example.scannerone.ui.screens

import android.content.Context
import android.net.wifi.ScanResult
import android.util.Log
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
import androidx.compose.ui.unit.dp
import com.example.scannerone.Services.ScanService.WifiScanServiceImpl
import com.example.scannerone.database.AppDatabase
import com.example.scannerone.permissions.rememberWifiPermissionState
import com.example.scannerone.repository.WifiScanRepository
import com.example.scannerone.services.GPSService.LocationManagerGPSServiceImpl
import com.example.scannerone.services.WarDrivingService.WarDrivingServiceImpl
import kotlinx.coroutines.launch

@Composable
fun WifiScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanResults by remember { mutableStateOf<List<ScanResult>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isScanning by remember { mutableStateOf(false) }

    // WarDriving test state
    var isWarDriving by remember { mutableStateOf(false) }
    var warDriveLog by remember { mutableStateOf<String?>(null) }

    val permissionState = rememberWifiPermissionState(
        onGranted = { Toast.makeText(context, "Permessi concessi", Toast.LENGTH_SHORT).show() },
        onDenied = { denied ->
            errorMessage = "Permessi negati: ${denied.joinToString(", ")}"
        }
    )

    fun isThrottleEnabled(context: Context): Boolean {
        // isScanThrottleEnabled e' disponibile solo da API 29 (Android 10)
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            wifiManager?.isScanThrottleEnabled == true
        } else {
            false // Su versioni vecchie non possiamo saperlo con certezza o non esiste l'API pubblica
        }
    }

    val showThrottleWarning = remember { isThrottleEnabled(context) }

    fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        return locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true ||
                locationManager?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
    }

    fun runScan() {
        if (!isLocationEnabled(context)) {
            Toast.makeText(context, "Per scansionare il Wi-Fi serve la geolocalizzazione attiva.", Toast.LENGTH_LONG).show()
            val intent = android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            context.startActivity(intent)
            return
        }

        permissionState.runWithPermission {
            scope.launch {
                isScanning = true
                errorMessage = null
                scanResults = emptyList()

                try {
                    val service = WifiScanServiceImpl(context)
                    val results = service.scan()

                    scanResults = results

                    // Log
                    Log.d("WifiScreen", "Reti trovate: ${results.size}")
                    results.forEach { r ->
                        Log.d("WifiScreen", "SSID: ${r.SSID} | BSSID: ${r.BSSID} | Signal: ${r.level} dBm")
                    }

                    // Toast
                    val toastMsg = if (results.isEmpty()) "Nessuna rete trovata"
                    else "${results.size} reti trovate"
                    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()

                } catch (e: Exception) {
                    val msg = e.message ?: "Errore sconosciuto"
                    errorMessage = msg
                    Log.e("WifiScreen", "Errore durante la scansione: $msg")
                    Toast.makeText(context, "Scansione fallita: $msg", Toast.LENGTH_LONG).show()
                } finally {
                    isScanning = false
                }
            }
        }
    }

    fun runWarDriveScan() {
        permissionState.runWithPermission {
            scope.launch {
                isWarDriving = true
                warDriveLog = null
                errorMessage = null

                try {
                    val gpsService = LocationManagerGPSServiceImpl(context)
                    val scanService = WifiScanServiceImpl(context)
                    val repository = WifiScanRepository(
                        AppDatabase.getDatabase(context).wifiScanDao()
                    )
                    val warDrivingService = WarDrivingServiceImpl(scanService, gpsService, repository)

                    Log.d("WarDriveTest", "========================================")
                    Log.d("WarDriveTest", "=== INIZIO TEST WARDRIVING SERVICE ===")
                    Log.d("WarDriveTest", "========================================")

                    val result = warDrivingService.performScan()

                    val logMsg = buildString {
                        appendLine("=== WARDRIVING COMPLETATO ===")
                        appendLine("Posizione GPS: lat=${result.position.latitude}, lon=${result.position.longitude}")
                        appendLine("Accuratezza GPS: ${result.position.accuracy} m")
                        appendLine("Reti trovate: ${result.networksFound}")
                        appendLine("Reti salvate: ${result.networksSaved}")
                    }

                    Log.d("WarDriveTest", logMsg)
                    warDriveLog = logMsg

                    Toast.makeText(
                        context,
                        "WarDrive OK: ${result.networksSaved}/${result.networksFound} reti salvate",
                        Toast.LENGTH_LONG
                    ).show()

                } catch (e: Exception) {
                    val msg = e.message ?: "Errore sconosciuto"
                    Log.e("WarDriveTest", "ERRORE WARDRIVING: $msg", e)
                    errorMessage = "WarDrive fallito: $msg"
                    warDriveLog = "ERRORE: $msg"
                    Toast.makeText(context, "WarDrive fallito: $msg", Toast.LENGTH_LONG).show()
                } finally {
                    isWarDriving = false
                }
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

        Button(
            onClick = { runScan() },
            enabled = !isScanning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isScanning) "Scansione in corso..." else "Avvia Scansione")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { runWarDriveScan() },
            enabled = !isWarDriving && !isScanning,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isWarDriving) "WarDriving in corso..." else "📡 Test WarDriving")
        }

        // WarDrive log output
        warDriveLog?.let { log ->
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "📋 Output WarDriving",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        }



        // Errore
        errorMessage?.let { err ->
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
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Risultati
        if (scanResults.isNotEmpty()) {
            Text(
                text = "${scanResults.size} reti trovate",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(bottom = 8.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(scanResults) { result ->
                    WifiResultCard(result)
                }
            }
        }
    }
}

@Composable
private fun WifiResultCard(result: ScanResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = result.SSID.ifBlank { "(rete nascosta)" },
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "BSSID: ${result.BSSID}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Text(
                text = "Segnale: ${result.level} dBm",
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    result.level >= -60 -> Color(0xFF2E7D32)   // verde: segnale forte
                    result.level >= -75 -> Color(0xFFF57F17)   // giallo: segnale medio
                    else                -> Color(0xFFC62828)   // rosso: segnale debole
                }
            )
        }
    }
}