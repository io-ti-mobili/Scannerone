package com.example.scannerone.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scannerone.viewmodel.WifiScanViewModel
import java.util.Locale

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun DatabaseScreen(
    modifier: Modifier = Modifier,
    viewModel: WifiScanViewModel = viewModel(),
    onOpenMap: (Double, Double, Int) -> Unit = { _, _, _ -> }
) {
    val networks by viewModel.networks.collectAsState()
    val draftConfig by viewModel.draftConfig.collectAsState()
    val appliedConfig by viewModel.config.collectAsState()


    var searchAddress by remember { mutableStateOf("") }
    var searchSsid by remember { mutableStateOf("") }
    var searchBssid by remember { mutableStateOf("") }

    var typeDropdownExpanded by remember { mutableStateOf(false) }

    var secDropdownExpanded by remember { mutableStateOf(false) }
    val secOptions = listOf("Tutte", "WPA", "WPA1", "WPA2", "WPA3")
    var selectedSecurity by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = {//utilizzato solo per test per generare dati mock
                //Generiamo un MAC Address casuale e univoco (es. "F3:1A:C9:8B:42:E1")
                val hexChars = "0123456789ABCDEF"
                val randomBssid = (1..6).joinToString(":") {
                    "${hexChars.random()}${hexChars.random()}"
                }

                //Inseriamo la rete finta usando il MAC appena generato
                viewModel.insertScannedNetwork(
                    bssid = randomBssid,
                    ssid = "Router_${randomBssid.takeLast(5)}",
                    capabilities = "[WPA2-PSK-CCMP]",
                    frequency = listOf(2412, 5180, 5500).random(),
                    rssi = -(30..90).random(),
                    lat = 45.4642 + Math.random() * 0.05,
                    lon = 9.1900 + Math.random() * 0.05,
                    accuracy = (5..15).random().toFloat()
                )
            }) {
                Text("+ Mock")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {


            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Cerca Rete",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp, top = 8.dp)
                )
                OutlinedTextField(
                    value = searchAddress,
                    onValueChange = { searchAddress = it },
                    label = { Text("Ricerca Indirizzo/Paese") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchSsid,
                        onValueChange = { searchSsid = it },
                        label = { Text("SSID") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = searchBssid,
                        onValueChange = { searchBssid = it },
                        label = { Text("BSSID") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    @OptIn(ExperimentalMaterial3Api::class)
                    ExposedDropdownMenuBox(
                        expanded = secDropdownExpanded,
                        onExpandedChange = { secDropdownExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedSecurity,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Sicurezza") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = secDropdownExpanded) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = secDropdownExpanded,
                            onDismissRequest = { secDropdownExpanded = false }
                        ) {
                            secOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        selectedSecurity = option
                                        secDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                }

                // ==========================================
                // TASTI CERCA E AZZERA
                // ==========================================
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            // ESEGUE LA RICERCA!
                            viewModel.applyFilters(
                                searchAddress,
                                searchSsid,
                                searchBssid,
                                if (selectedSecurity.isEmpty()) "Tutte" else selectedSecurity
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Search,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Cerca")
                    }

                    OutlinedButton(
                        onClick = {
                            // AZZERA I CAMPI E LA RICERCA
                            searchAddress = ""
                            searchSsid = ""
                            searchBssid = ""
                            selectedSecurity = ""
                            viewModel.applyFilters("", "", "", "Tutte")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Azzera")
                    }
                }


            }

            if (networks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text("Nessuna rete trovata.")
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
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "SSID: ${net.ssid}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(text = "MAC: ${net.bssid}", style = MaterialTheme.typography.bodyMedium)

                                    if (net.realCity != null) {
                                        val addressStr = if (net.realStreet != null) "${net.realCity}, ${net.realStreet}" else net.realCity
                                        Text(text = "📍 $addressStr", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 4.dp))
                                    }
                                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = "Freq: ${net.frequency} MHz")
                                        if (net.realLatitude != null && net.realLongitude != null) {
                                            val latFmt = String.format(java.util.Locale.getDefault(), "%.5f", net.realLatitude)
                                            val lonFmt = String.format(java.util.Locale.getDefault(), "%.5f", net.realLongitude)
                                            val accFmt = net.estAccuracy?.toInt()?.toString() ?: "?"
                                            Text(text = "Pos: $latFmt, $lonFmt (±${accFmt}m)")
                                        } else {
                                            Text(text = "Posizione: In elaborazione...")
                                        }
                                    }
                                }


                                if (net.realLatitude != null && net.realLongitude != null) {
                                    IconButton(
                                        onClick = {
                                            val lat = net.realLatitude
                                            val lon = net.realLongitude

                                            onOpenMap(lat, lon, net.id)
                                        }
                                    ) {
                                        androidx.compose.material3.Icon(
                                            imageVector = androidx.compose.material.icons.Icons.Default.LocationOn,
                                            contentDescription = "Apri nella mappa",
                                            modifier = Modifier.size(36.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
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
