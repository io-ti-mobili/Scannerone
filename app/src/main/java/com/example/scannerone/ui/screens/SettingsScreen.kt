package com.example.scannerone.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scannerone.viewmodel.WifiScanViewModel
import com.example.scannerone.viewmodel.StrategyType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: WifiScanViewModel = viewModel()
) {
    val draftConfig by viewModel.draftConfig.collectAsState()
    val appliedConfig by viewModel.config.collectAsState()

    var showExportDialog by remember { mutableStateOf(false) }
    var exportFormat by remember { mutableStateOf("CSV") }
    var exportScans by remember { mutableStateOf(true) }
    var exportNetworks by remember { mutableStateOf(true) }
    var exportSessions by remember { mutableStateOf(false) }

    // ---- Logica Lingua e Tema ----
    val context = androidx.compose.ui.platform.LocalContext.current
    val systemLang = context.resources.configuration.locales[0].language
    val initialLang = if (systemLang == "it") "Italiano" else "English"
    val systemInDark = androidx.compose.foundation.isSystemInDarkTheme()
    
    var selectedLanguage by remember { mutableStateOf(initialLang) }
    var selectedTheme by remember { mutableStateOf("Sistema") }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // ---- Configurazione Motore Matematico ----
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
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
                        selected = draftConfig.baseStrategyType == StrategyType.CENTROID,
                        onClick = {
                            viewModel.updateDraftConfig(
                                draftConfig.copy(baseStrategyType = StrategyType.CENTROID)
                            )
                        }
                    )
                    Text("Weighted Centroid", style = MaterialTheme.typography.bodySmall)

                    Spacer(modifier = Modifier.width(8.dp))

                    RadioButton(
                        selected = draftConfig.baseStrategyType == StrategyType.TRILATERATION,
                        onClick = {
                            viewModel.updateDraftConfig(
                                draftConfig.copy(baseStrategyType = StrategyType.TRILATERATION)
                            )
                        }
                    )
                    Text("Trilateration", style = MaterialTheme.typography.bodySmall)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = draftConfig.useRansac,
                        onCheckedChange = {
                            viewModel.updateDraftConfig(draftConfig.copy(useRansac = it))
                        }
                    )
                    Text("Applica Filtraggio RANSAC (Scarta Outliers)", style = MaterialTheme.typography.bodySmall)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = draftConfig.useGpsWeight,
                        onCheckedChange = {
                            viewModel.updateDraftConfig(draftConfig.copy(useGpsWeight = it))
                        }
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

        // ---- Personalizzazione (Lingua e Tema) ----
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Personalizzazione (fanno schifo vabbè)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                // Selettore Lingua
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Lingua App", style = MaterialTheme.typography.bodyMedium)
                    Row {
                        FilterChip(
                            selected = selectedLanguage == "Italiano",
                            onClick = { /* Non fa nulla */ },
                            label = { Text("Italiano") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = selectedLanguage == "English",
                            onClick = { /* Non fa nulla */ },
                            label = { Text("English") }
                        )
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                // Selettore Tema
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Tema App", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = selectedTheme == "Chiaro",
                            onClick = { /* Non fa nulla */ },
                            label = { Text("Chiaro", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = selectedTheme == "Scuro",
                            onClick = { /* Non fa nulla */ },
                            label = { Text("Scuro", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = selectedTheme == "Sistema",
                            onClick = { /* Non fa nulla */ },
                            label = { Text("Sistema", fontSize = 10.sp) }
                        )
                    }
                }
            }
        }

        // ---- Gestione Dati ----
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Gestione Dati",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showExportDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Esporta Dati")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { /* TODO: Import */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Importa Dati")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { /* TODO: Cancella DB */ },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Cancella DB")
                }
            }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Esporta Dati") },
            text = {
                Column {
                    Text("Formato (scegli uno):", style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = exportFormat == "CSV",
                            onClick = { exportFormat = "CSV" }
                        )
                        Text("CSV")
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(
                            selected = exportFormat == "JSON",
                            onClick = { exportFormat = "JSON" }
                        )
                        Text("JSON")
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Entità da esportare (selezione multipla):", style = MaterialTheme.typography.titleSmall)
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = exportScans, onCheckedChange = { exportScans = it })
                        Text("Scansioni")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = exportNetworks, onCheckedChange = { exportNetworks = it })
                        Text("Reti")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = exportSessions, onCheckedChange = { exportSessions = it })
                        Text("Sessioni")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    // TODO: perform export based on selection
                    showExportDialog = false
                }) {
                    Text("Esporta")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Annulla")
                }
            }
        )
    }
}
