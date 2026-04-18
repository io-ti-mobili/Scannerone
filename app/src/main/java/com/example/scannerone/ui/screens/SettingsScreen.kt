package com.example.scannerone.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scannerone.io.ExportFormat
import com.example.scannerone.io.ExportSelection
import com.example.scannerone.io.ExportState
import com.example.scannerone.io.ImportState
import com.example.scannerone.viewmodel.ExportImportViewModel
import com.example.scannerone.viewmodel.WifiScanViewModel
import com.example.scannerone.viewmodel.StrategyType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: WifiScanViewModel = viewModel(),
    exportImportViewModel: ExportImportViewModel = viewModel()
) {
    val draftConfig by viewModel.draftConfig.collectAsState()
    val appliedConfig by viewModel.config.collectAsState()

    val exportState by exportImportViewModel.exportState.collectAsState()
    val importState by exportImportViewModel.importState.collectAsState()

    val context = LocalContext.current

    // ---- Stato dialoghi ----
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportFormatDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    var exportFormat by remember { mutableStateOf("CSV") }
    var exportNetworks by remember { mutableStateOf(true) }
    var exportSessions by remember { mutableStateOf(false) }
    var exportScans by remember { mutableStateOf(true) }

    var importFormat by remember { mutableStateOf("CSV") }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // Salva la selezione export al momento del click — usata dal launcher callback
    var pendingExportFormat by remember { mutableStateOf(ExportFormat.CSV) }
    var pendingExportSelection by remember { mutableStateOf(ExportSelection()) }

    // ---- Launcher per CREARE il file di export (apre prima del export) ----
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        // L'utente ha scelto dove salvare → ora esportiamo direttamente sull'URI
        if (uri != null) {
            exportImportViewModel.onExportClick(pendingExportFormat, pendingExportSelection, uri)
        }
    }

    // ---- Launcher per scegliere il file da importare ----
    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showImportFormatDialog = true
        }
    }

    // ---- Feedback stato export ----
    LaunchedEffect(exportState) {
        when (val state = exportState) {
            is ExportState.Success -> {
                Toast.makeText(context, "File esportato con successo!", Toast.LENGTH_SHORT).show()
                exportImportViewModel.resetExportState()
            }
            is ExportState.Error -> {
                Toast.makeText(context, "Errore export: ${state.message}", Toast.LENGTH_LONG).show()
                exportImportViewModel.resetExportState()
            }
            else -> {}
        }
    }

    // ---- Feedback stato import ----
    LaunchedEffect(importState) {
        when (val state = importState) {
            is ImportState.Success -> {
                Toast.makeText(context, "Import completato con successo!", Toast.LENGTH_SHORT).show()
                exportImportViewModel.resetImportState()
            }
            is ImportState.Error -> {
                Toast.makeText(context, "Errore import: ${state.message}", Toast.LENGTH_LONG).show()
                exportImportViewModel.resetImportState()
            }
            else -> {}
        }
    }

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

                // ---- Bottone Export ----
                Button(
                    onClick = { showExportDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = exportState !is ExportState.Loading && importState !is ImportState.Loading
                ) {
                    if (exportState is ExportState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Esportazione in corso...")
                    } else {
                        Text("Esporta Dati")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ---- Bottone Import ----
                Button(
                    onClick = {
                        openFileLauncher.launch(arrayOf("application/json", "application/zip", "*/*"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = importState !is ImportState.Loading && exportState !is ExportState.Loading
                ) {
                    if (importState is ImportState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Importazione in corso...")
                    } else {
                        Text("Importa Dati")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ---- Bottone Cancella DB ----
                Button(
                    onClick = { showDeleteConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = importState !is ImportState.Loading && exportState !is ExportState.Loading
                ) {
                    Text("Cancella DB")
                }
            }
        }
    }

    // ============================================================
    // DIALOG EXPORT — Il file picker si apre DOPO la conferma,
    // poi onExportClick riceve l'URI direttamente
    // ============================================================
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Esporta Dati") },
            text = {
                Column {
                    Text("Formato (scegli uno):", style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = exportFormat == "CSV", onClick = { exportFormat = "CSV" })
                        Text("CSV (.zip)")
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(selected = exportFormat == "JSON", onClick = { exportFormat = "JSON" })
                        Text("JSON")
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Entità da esportare:", style = MaterialTheme.typography.titleSmall)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = exportNetworks, onCheckedChange = { exportNetworks = it })
                        Text("Reti")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = exportSessions, onCheckedChange = { exportSessions = it })
                        Text("Sessioni")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = exportScans, onCheckedChange = { exportScans = it })
                        Text("Scansioni")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExportDialog = false
                        // Salva selezione — il launcher callback la userà
                        pendingExportFormat = if (exportFormat == "JSON") ExportFormat.JSON else ExportFormat.CSV
                        pendingExportSelection = ExportSelection(
                            includiNetworks = exportNetworks,
                            includiSessions = exportSessions,
                            includiRecords  = exportScans
                        )
                        // Apri file picker: l'utente sceglie dove salvare
                        val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        val ext = if (exportFormat == "CSV") "zip" else "json"
                        saveFileLauncher.launch("scannerone_export_$dateStr.$ext")
                        // L'export vero parte nel saveFileLauncher callback, dopo ricezione URI
                    },
                    enabled = exportNetworks || exportSessions || exportScans
                ) {
                    Text("Scegli destinazione…")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Annulla") }
            }
        )
    }

    // ============================================================
    // DIALOG SCELTA FORMATO IMPORT
    // ============================================================
    if (showImportFormatDialog) {
        AlertDialog(
            onDismissRequest = {
                showImportFormatDialog = false
                pendingImportUri = null
            },
            title = { Text("Formato del file") },
            text = {
                Column {
                    Text("Seleziona il formato del file da importare:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = importFormat == "CSV", onClick = { importFormat = "CSV" })
                        Text("CSV (file .zip)")
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(selected = importFormat == "JSON", onClick = { importFormat = "JSON" })
                        Text("JSON")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "ℹ️ L'import aggiunge i dati a quelli già presenti. Le reti con lo stesso BSSID non vengono duplicate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showImportFormatDialog = false
                    val uri = pendingImportUri
                    if (uri != null) {
                        val formato = if (importFormat == "JSON") ExportFormat.JSON else ExportFormat.CSV
                        exportImportViewModel.onImportClick(uri, formato)
                    }
                    pendingImportUri = null
                }) {
                    Text("Importa")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportFormatDialog = false
                    pendingImportUri = null
                }) {
                    Text("Annulla")
                }
            }
        )
    }

    // ============================================================
    // DIALOG CONFERMA CANCELLAZIONE DB
    // ============================================================
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Cancella Database") },
            text = {
                Text(
                    "Sei sicuro di voler cancellare tutti i dati? Questa operazione è irreversibile.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        exportImportViewModel.onDeleteAllClick()
                        Toast.makeText(context, "Database cancellato.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Cancella Tutto")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Annulla") }
            }
        )
    }
}
