package com.example.scannerone.ui.screens

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scannerone.R
import com.example.scannerone.io.ExportFormat
import com.example.scannerone.io.ExportSelection
import com.example.scannerone.io.ExportState
import com.example.scannerone.io.ImportState
import com.example.scannerone.viewmodel.ExportImportViewModel
import com.example.scannerone.viewmodel.StrategyViewModel
import com.example.scannerone.viewmodel.StrategyType
import com.example.scannerone.viewmodel.RegistrationState
import com.example.scannerone.viewmodel.UploadState
import com.example.scannerone.viewmodel.UploadViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    isDark: Boolean,
    themePreference: Boolean?,
    appLanguage: String,
    onThemeChange: (Boolean?) -> Unit,
    onLanguageChange: (String) -> Unit,
    viewModel: StrategyViewModel = viewModel(),
    exportImportViewModel: ExportImportViewModel = viewModel(),
    uploadViewModel: UploadViewModel = viewModel()
) {
    val draftConfig by viewModel.draftConfig.collectAsState()
    val appliedConfig by viewModel.config.collectAsState()

    val userUuid by uploadViewModel.userUuid.collectAsState()
    val username by uploadViewModel.username.collectAsState()
    val password by uploadViewModel.password.collectAsState()
    val serverEndpoint by uploadViewModel.serverEndpoint.collectAsState()
    val uploadState by uploadViewModel.uploadState.collectAsState()
    val registrationState by uploadViewModel.registrationState.collectAsState()

    val exportState by exportImportViewModel.exportState.collectAsState()
    val importState by exportImportViewModel.importState.collectAsState()

    val context = LocalContext.current

    // ---- Stato edit credentials ----
    var showCredentialsDialog by remember { mutableStateOf(false) }
    var draftUsername by remember { mutableStateOf("") }
    var draftUuid by remember { mutableStateOf("") }
    var draftPassword by remember { mutableStateOf("") }
    var draftEndpoint by remember { mutableStateOf("") }

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
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_toast_export_success),
                    Toast.LENGTH_SHORT
                ).show()
                exportImportViewModel.resetExportState()
            }
            is ExportState.Error -> {
                val message = state.message?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.common_error_unknown)
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_toast_export_error, message),
                    Toast.LENGTH_LONG
                ).show()
                exportImportViewModel.resetExportState()
            }
            else -> {}
        }
    }

    // ---- Feedback stato import ----
    LaunchedEffect(importState) {
        when (val state = importState) {
            is ImportState.Success -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_toast_import_success),
                    Toast.LENGTH_SHORT
                ).show()
                exportImportViewModel.resetImportState()
            }
            is ImportState.Error -> {
                val message = state.message?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.common_error_unknown)
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_toast_import_error, message),
                    Toast.LENGTH_LONG
                ).show()
                exportImportViewModel.resetImportState()
            }
            else -> {}
        }
    }

    // ---- Feedback stato upload ----
    LaunchedEffect(uploadState) {
        when (val state = uploadState) {
            is UploadState.Success -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                uploadViewModel.resetUploadState()
            }
            is UploadState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                uploadViewModel.resetUploadState()
            }
            else -> {}
        }
    }

    // ---- Feedback stato registrazione ----
    LaunchedEffect(registrationState) {
        when (val state = registrationState) {
            is RegistrationState.Success -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                uploadViewModel.resetRegistrationState()
            }
            is RegistrationState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                uploadViewModel.resetRegistrationState()
            }
            else -> {}
        }
    }

    fun themePreferenceToKey(value: Boolean?): String {
        return when (value) {
            true -> "DARK"
            false -> "LIGHT"
            null -> "SYSTEM"
        }
    }

    // Inizializza dal DataStore: null=Sistema, true=Scuro, false=Chiaro
    var selectedTheme by remember {
        mutableStateOf(themePreferenceToKey(themePreference))
    }
    LaunchedEffect(themePreference) {
        selectedTheme = themePreferenceToKey(themePreference)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        // ---- Configurazione Motore Matematico ----
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.settings_math_engine_title),
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
                    Text(stringResource(R.string.settings_strategy_weighted_centroid), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(8.dp))
                    RadioButton(
                        selected = draftConfig.baseStrategyType == StrategyType.TRILATERATION,
                        onClick = {
                            viewModel.updateDraftConfig(
                                draftConfig.copy(baseStrategyType = StrategyType.TRILATERATION)
                            )
                        }
                    )
                    Text(stringResource(R.string.settings_strategy_trilateration), style = MaterialTheme.typography.bodySmall)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = draftConfig.useRansac,
                        onCheckedChange = { viewModel.updateDraftConfig(draftConfig.copy(useRansac = it)) }
                    )
                    Text(stringResource(R.string.settings_ransac_filter), style = MaterialTheme.typography.bodySmall)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = draftConfig.useGpsWeight,
                        onCheckedChange = { viewModel.updateDraftConfig(draftConfig.copy(useGpsWeight = it)) }
                    )
                    Text(stringResource(R.string.settings_gps_weight), style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.applyDraftAndRecalculate() },
                    enabled = draftConfig != appliedConfig,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_apply_recalculate))
                }
            }
        }

        // ---- Personalizzazione (Lingua e Tema) ----
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.settings_customization_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                // Selettore Lingua
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.settings_app_language), style = MaterialTheme.typography.bodyMedium)
                    Row {
                        FilterChip(
                            selected = appLanguage == "it",
                            onClick = { onLanguageChange("it") },
                            label = { Text(stringResource(R.string.language_italian)) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = appLanguage == "en",
                            onClick = { onLanguageChange("en") },
                            label = { Text(stringResource(R.string.language_english)) }
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
                    Text(stringResource(R.string.settings_app_theme), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = selectedTheme == "LIGHT",
                            onClick = {
                                selectedTheme = "LIGHT"
                                onThemeChange(false)
                            },
                            label = { Text(stringResource(R.string.theme_light), fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = selectedTheme == "DARK",
                            onClick = {
                                selectedTheme = "DARK"
                                onThemeChange(true)
                            },
                            label = { Text(stringResource(R.string.theme_dark), fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = selectedTheme == "SYSTEM",
                            onClick = {
                                selectedTheme = "SYSTEM"
                                onThemeChange(null)
                            },
                            label = { Text(stringResource(R.string.theme_system), fontSize = 10.sp) }
                        )
                    }
                }
            }
        }

                // ---- Sincronizzazione Web ----
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.settings_web_sync_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        draftUsername = username
                        draftUuid = userUuid
                        draftPassword = password
                        draftEndpoint = serverEndpoint
                        showCredentialsDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_edit_credentials))
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { uploadViewModel.uploadNetworks() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uploadState !is UploadState.Loading && username.isNotBlank()
                ) {
                    if (uploadState is UploadState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_sync_in_progress))
                    } else if (username.isBlank()) {
                        Text(stringResource(R.string.settings_set_username_sync))
                    } else {
                        Text(stringResource(R.string.settings_sync_site))
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
                    stringResource(R.string.settings_data_management_title),
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
                        Text(stringResource(R.string.settings_export_in_progress))
                    } else {
                        Text(stringResource(R.string.settings_export_data))
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
                        Text(stringResource(R.string.settings_import_in_progress))
                    } else {
                        Text(stringResource(R.string.settings_import_data))
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
                    Text(stringResource(R.string.settings_delete_db))
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
            title = { Text(stringResource(R.string.settings_export_data)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_format_choose), style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = exportFormat == "CSV", onClick = { exportFormat = "CSV" })
                        Text(stringResource(R.string.settings_format_csv_zip))
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(selected = exportFormat == "JSON", onClick = { exportFormat = "JSON" })
                        Text(stringResource(R.string.settings_format_json))
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.settings_entities_to_export), style = MaterialTheme.typography.titleSmall)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = exportNetworks, onCheckedChange = { exportNetworks = it })
                        Text(stringResource(R.string.common_networks))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = exportSessions, onCheckedChange = { exportSessions = it })
                        Text(stringResource(R.string.common_sessions))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = exportScans, onCheckedChange = { exportScans = it })
                        Text(stringResource(R.string.common_scans))
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
                    Text(stringResource(R.string.settings_choose_destination))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text(stringResource(R.string.common_cancel)) }
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
            title = { Text(stringResource(R.string.settings_file_format_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_select_import_format), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = importFormat == "CSV", onClick = { importFormat = "CSV" })
                        Text(stringResource(R.string.settings_format_csv_file_zip))
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(selected = importFormat == "JSON", onClick = { importFormat = "JSON" })
                        Text(stringResource(R.string.settings_format_json))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.settings_import_info),
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
                    Text(stringResource(R.string.settings_import_data))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportFormatDialog = false
                    pendingImportUri = null
                }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // ============================================================
    // DIALOG EDIT CREDENTIALS
    // ============================================================
    if (showCredentialsDialog) {
        AlertDialog(
            onDismissRequest = { showCredentialsDialog = false },
            title = { Text(stringResource(R.string.settings_credentials_dialog_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = draftEndpoint,
                        onValueChange = { draftEndpoint = it },
                        label = { Text(stringResource(R.string.settings_server_endpoint_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draftUsername,
                        onValueChange = { draftUsername = it },
                        label = { Text(stringResource(R.string.settings_username_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draftUuid,
                        onValueChange = { draftUuid = it },
                        label = { Text(stringResource(R.string.settings_user_uuid_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draftPassword,
                        onValueChange = { draftPassword = it },
                        label = { Text(stringResource(R.string.settings_password_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            uploadViewModel.registerUser(draftEndpoint) { newUuid, newPassword ->
                                draftUuid = newUuid
                                draftPassword = newPassword
                            }
                        },
                        enabled = registrationState !is RegistrationState.Loading
                    ) {
                        if (registrationState is RegistrationState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.common_register))
                        }
                    }
                    Button(
                        onClick = {
                            uploadViewModel.saveCredentials(draftUsername, draftUuid, draftPassword, draftEndpoint)
                            showCredentialsDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCredentialsDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    // ============================================================
    // DIALOG CONFERMA CANCELLAZIONE DB
    // ============================================================
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(stringResource(R.string.settings_delete_database_title)) },
            text = {
                Text(
                    stringResource(R.string.settings_delete_database_confirmation),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        exportImportViewModel.onDeleteAllClick()
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_toast_database_deleted),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.settings_delete_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}
