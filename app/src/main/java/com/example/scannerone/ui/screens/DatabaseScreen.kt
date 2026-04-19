package com.example.scannerone.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Update
import java.util.Locale
import kotlin.math.ceil

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun DatabaseScreen(
    modifier: Modifier = Modifier,
    viewModel: WifiScanViewModel = viewModel(),
    onOpenMap: (Double, Double, Int) -> Unit = { _, _, _ -> }
) {
    val networks by viewModel.networks.collectAsState()
    val dbPageSize = viewModel.getDbPageSize()
    val currentPage by viewModel.currentPage.collectAsState()
    val hasPreviousPage by viewModel.hasPreviousPage.collectAsState()
    val canGoNextPage by viewModel.canGoNextPage.collectAsState()
    val totalFilteredNetworks by viewModel.totalFilteredNetworks.collectAsState()
    val isPagingBusy by viewModel.isPagingBusy.collectAsState()
    val draftConfig by viewModel.draftConfig.collectAsState()
    val appliedConfig by viewModel.config.collectAsState()

    var searchAddress by remember { mutableStateOf("") }
    var searchSsid by remember { mutableStateOf("") }
    var searchBssid by remember { mutableStateOf("") }

    var secDropdownExpanded by remember { mutableStateOf(false) }
    val secOptions = listOf("Tutte", "WPA3", "WPA2", "WPA", "WEP", "Open")
    var selectedSecurity by remember { mutableStateOf("Tutte") }

    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(onClick = { 
                    viewModel.generateYearlyMock()
                    Toast.makeText(context, "Mock Annuale", Toast.LENGTH_SHORT).show()
                }, containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                    Icon(Icons.Default.History, contentDescription = "Mock Anno")
                }
                SmallFloatingActionButton(onClick = { 
                    viewModel.generateMonthlyMock()
                    Toast.makeText(context, "Mock Mensile", Toast.LENGTH_SHORT).show()
                }, containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = "Mock Mese")
                }
                SmallFloatingActionButton(onClick = { 
                    viewModel.generateWeeklyMock()
                    Toast.makeText(context, "Mock Settimana", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.Update, contentDescription = "Mock Settimana")
                }
                ExtendedFloatingActionButton(
                    onClick = { 
                        viewModel.generateMockSession()
                        Toast.makeText(context, "Mock Ora", Toast.LENGTH_SHORT).show()
                    },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Mock Ora") }
                )
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

            // 5. BARRA PAGINAZIONE IN FONDO ALLA LISTA
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Pagina ${currentPage + 1}/${ceil(totalFilteredNetworks.toDouble() / dbPageSize).toInt()}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (isPagingBusy) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.goToPreviousPage() },
                                enabled = hasPreviousPage && !isPagingBusy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Precedente")
                            }

                            Button(
                                onClick = { viewModel.goToNextPage() },
                                enabled = canGoNextPage,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Successiva")
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ){
                            Text(
                                text = "Reti visualizzate: $totalFilteredNetworks",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}