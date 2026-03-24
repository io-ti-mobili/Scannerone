package com.example.scannerone.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scannerone.viewmodel.WifiScanViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DatabaseScreen(
    modifier: Modifier = Modifier,
    viewModel: WifiScanViewModel = viewModel()
) {
    val scansioni by viewModel.scansioni.collectAsState()

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val mockScan = com.example.scannerone.entities.WifiScan(
                    ssid = "Rete_Finta_${(10..99).random()}",
                    bssid = "00:11:22:33:44:${(10..99).random()}",
                    rssi = -(30..90).random(),
                    frequency = listOf(2412, 5180, 5200).random(),
                    capabilities = "[WPA2-PSK-CCMP]",
                    latitude = 45.4642,
                    longitude = 9.1900,
                    timestamp = System.currentTimeMillis()
                )
                viewModel.insertScan(mockScan)
            }) {
                Text("+ Mock")
            }
        }
    ) { innerPadding ->
        if (scansioni.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Nessuna rete nel database... in attesa.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(scansioni) { scan ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "SSID: ${scan.ssid}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text(text = "MAC: ${scan.bssid}", style = MaterialTheme.typography.bodyMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Segnale: ${scan.rssi} dBm")
                                val orario = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(scan.timestamp))
                                Text(text = "Ore: $orario")
                            }
                        }
                    }
                }
            }
        }
    }
}