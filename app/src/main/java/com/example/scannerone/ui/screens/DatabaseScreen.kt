package com.example.scannerone.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
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

    var secDropdownExpanded by remember { mutableStateOf(false) }
    val secOptions = listOf("Tutte", "WPA", "WPA1", "WPA2", "WPA3")
    var selectedSecurity by remember { mutableStateOf(secOptions[0]) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { // utilizzato solo per test per generare dati mock
                val hexChars = "0123456789ABCDEF"
                val randomBssid = (1..6).joinToString(":") {
                    "${hexChars.random()}${hexChars.random()}"
                }
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

        // =================================================================
        // LAZYCOLUMN PRINCIPALE CHE AVVOLGE TUTTA LA SCHERMATA
        // =================================================================
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp), // bottom = 80.dp evita che il FAB copra l'ultima card
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 1. BLOCCO CONFIGURAZIONE MOTORE MATEMATICO
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Configurazione Motore Matematico",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
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

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = draftConfig.useRansac,
                                onCheckedChange = { viewModel.updateDraftConfig(draftConfig.copy(useRansac = it)) }
                            )
                            Text("Applica Filtraggio RANSAC (Scarta Outliers)", style = MaterialTheme.typography.bodySmall)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = draftConfig.useGpsWeight,
                                onCheckedChange = { viewModel.updateDraftConfig(draftConfig.copy(useGpsWeight = it)) }
                            )
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
            }

            // 2. BLOCCO RICERCA E FILTRI
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchAddress,
                        onValueChange = { searchAddress = it },
                        label = { Text("Ricerca Indirizzo/Paese") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                    ExposedDropdownMenuBox(
                        expanded = secDropdownExpanded,
                        onExpandedChange = { secDropdownExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedSecurity,
                            onValueChange = {},
                            readOnly = true,
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

                    // TASTI CERCA E AZZERA
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.applyFilters(searchAddress, searchSsid, searchBssid, selectedSecurity) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = androidx.compose.material.icons.Icons.Default.Search, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Cerca")
                        }

                        OutlinedButton(
                            onClick = {
                                searchAddress = ""
                                searchSsid = ""
                                searchBssid = ""
                                selectedSecurity = secOptions[0]
                                viewModel.applyFilters("", "", "", "Tutte")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Azzera")
                        }
                    }
                }
            }

            // 3. BLOCCO BOTTONI ESPORTAZIONE (Scorrimento Orizzontale)
            item {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Button(onClick = { }) { Text("Esporta CSV/KML Attuale") } }
                    item { Button(onClick = { }) { Text("Esporta DB Completo (CSV)") } }
                    item { Button(onClick = { }) { Text("Backup Database") } }
                    item { Button(onClick = { }) { Text("Importa Reti Osservate") } }
                    item { Button(onClick = { }) { Text("Salva DB in App") } }
                    item {
                        Button(
                            onClick = { },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text("Cancella DB") }
                    }
                }
            }

            // 4. LISTA DELLE RETI (Risultati della ricerca)
            if (networks.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Nessuna rete trovata.")
                    }
                }
            } else {
                items(networks, key = { it.id }) { net ->
                    var menuExpanded by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // 1. COLONNA TESTI
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SSID: ${net.ssid}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "MAC: ${net.bssid}",
                                    style = MaterialTheme.typography.bodyMedium
                                )

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
                                    Text(
                                        text = "Freq: ${net.frequency} MHz",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (net.realLatitude != null && net.realLongitude != null) {
                                        val latFmt = String.format(Locale.getDefault(), "%.5f", net.realLatitude)
                                        val lonFmt = String.format(Locale.getDefault(), "%.5f", net.realLongitude)
                                        val accFmt = net.estAccuracy?.toInt()?.toString() ?: "?"
                                        Text(
                                            text = "Pos: $latFmt, $lonFmt (±${accFmt}m)",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    } else {
                                        Text(
                                            text = "Posizione: In elaborazione...",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            // 2. MENU A 3 PALLINI
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.MoreVert,
                                        contentDescription = "Opzioni Rete"
                                    )
                                }

                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    val currentLat = net.realLatitude
                                    val currentLon = net.realLongitude
                                    val currId = net.id
                                    if (currentLat != null && currentLon != null) {
                                        DropdownMenuItem(
                                            text = { Text("Apri in Mappa") },
                                            leadingIcon = {
                                                Icon(
                                                    androidx.compose.material.icons.Icons.Default.LocationOn,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                onOpenMap(currentLat, currentLon, currId)
                                            }
                                        )
                                    }

                                    DropdownMenuItem(
                                        text = { Text("Elimina Rete", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = {
                                            Icon(
                                                androidx.compose.material.icons.Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.deleteNetwork(net)
                                        }
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