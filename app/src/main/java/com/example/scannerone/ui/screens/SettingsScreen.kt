package com.example.scannerone.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
