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
    val networks by viewModel.networks.collectAsState()
    val draftConfig by viewModel.draftConfig.collectAsState()
    val appliedConfig by viewModel.config.collectAsState()

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val bssidPool = listOf("00:11:22:33:44:55", "AA:BB:CC:DD:EE:FF", "12:34:56:78:90:AB")
                val mockBssid = bssidPool.random()
                
                viewModel.insertScannedNetwork(
                    bssid = mockBssid,
                    ssid = "Router_${mockBssid.takeLast(2)}",
                    capabilities = "[WPA2-PSK-CCMP]",
                    frequency = 2412,
                    rssi = -(30..90).random(),
                    lat = 45.4642 + Math.random() * 0.001,
                    lon = 9.1900 + Math.random() * 0.001,
                    accuracy = (5..15).random().toFloat()
                )
            }) {
                Text("+ Mock")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Configurazione Motore Matematico", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(
                            selected = draftConfig.baseStrategyType == com.example.scannerone.viewmodel.StrategyType.CENTROID,
                            onClick = { viewModel.updateDraftConfig(draftConfig.copy(baseStrategyType = com.example.scannerone.viewmodel.StrategyType.CENTROID)) }
                        )
                        Text("Weighted Centroid", style = MaterialTheme.typography.bodySmall)
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        RadioButton(
                            selected = draftConfig.baseStrategyType == com.example.scannerone.viewmodel.StrategyType.TRILATERATION,
                            onClick = { viewModel.updateDraftConfig(draftConfig.copy(baseStrategyType = com.example.scannerone.viewmodel.StrategyType.TRILATERATION)) }
                        )
                        Text("Trilateration", style = MaterialTheme.typography.bodySmall)
                    }
                    
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = draftConfig.useRansac, onCheckedChange = { viewModel.updateDraftConfig(draftConfig.copy(useRansac = it)) })
                        Text("Applica Filtraggio RANSAC (Scarta Outliers)", style = MaterialTheme.typography.bodySmall)
                    }
                    
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = draftConfig.useGpsWeight, onCheckedChange = { viewModel.updateDraftConfig(draftConfig.copy(useGpsWeight = it)) })
                        Text("Aggiungi Peso Precisione GPS", style = MaterialTheme.typography.bodySmall)
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = { viewModel.applyDraftAndRecalculate() },
                        enabled = draftConfig != appliedConfig,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Applica e Ricalcola DB")
                    }
                }
            }

            if (networks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("Nessuna rete nel database... in attesa.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(networks) { net ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "SSID: ${net.ssid}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text(text = "MAC: ${net.bssid}", style = MaterialTheme.typography.bodyMedium)
                            
                            if (net.realCity != null) {
                                val addressStr = if (net.realStreet != null) "${net.realCity}, ${net.realStreet}" else net.realCity
                                Text(
                                    text = "📍 $addressStr", 
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Freq: ${net.frequency} MHz")
                                if (net.realLatitude != null && net.realLongitude != null) {
                                    val latFmt = String.format(Locale.getDefault(), "%.5f", net.realLatitude)
                                    val lonFmt = String.format(Locale.getDefault(), "%.5f", net.realLongitude)
                                    val accFmt = net.estAccuracy?.toInt()?.toString() ?: "?"
                                    Text(text = "Pos: $latFmt, $lonFmt (±${accFmt}m)")
                                } else {
                                    Text(text = "Posizione: In elaborazione...")
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}