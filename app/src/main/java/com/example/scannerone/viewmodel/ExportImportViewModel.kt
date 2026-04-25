package com.example.scannerone.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.scannerone.R
import com.example.scannerone.database.AppDatabase
import com.example.scannerone.io.ExportBundle
import com.example.scannerone.io.ExportFormat
import com.example.scannerone.io.ExportSelection
import com.example.scannerone.io.ExportState
import com.example.scannerone.io.ImportState
import com.example.scannerone.io.SerializerFactory
import com.example.scannerone.repository.ImportExportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel dedicato alle operazioni di Export/Import.
 * Separato da WifiScanViewModel (Single Responsibility).
 * Tutto il lavoro pesante su Dispatchers.IO.
 */
class ExportImportViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repo = ImportExportRepository(
        importExportDao = db.importExportDao(),
        networkDao = db.networkDao(),
        sessionDao = db.sessionDao()
    )

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState = _exportState.asStateFlow()

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState = _importState.asStateFlow()

    /**
     * Export streaming diretto sull'URI scelto dall'utente.
     * L'OutputStream viene aperto qui — nessun ByteArray intermedio.
     * Le Sequence lazy leggono il DB a chunk da [pageSize] durante la scrittura.
     */
    fun onExportClick(formato: ExportFormat, selezione: ExportSelection, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            _exportState.value = ExportState.Loading
            try {
                val bundle = ExportBundle(
                    networks = repo.getNetworksSequence().takeIf { selezione.includiNetworks },
                    sessions = repo.getSessionsSequence().takeIf { selezione.includiSessions },
                    records  = repo.getRecordsSequence().takeIf { selezione.includiRecords },
                )

                val contentResolver = getApplication<Application>().contentResolver
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    SerializerFactory.get(formato).export(bundle, outputStream)
                } ?: throw IllegalStateException(app.getString(R.string.export_error_open_destination))

                _exportState.value = ExportState.Success
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(
                    e.message ?: app.getString(R.string.export_error_unknown)
                )
            }
        }
    }

    /**
     * Import con merge: legge l'InputStream, rimappa gli ID, aggiunge i dati
     * a quelli già presenti nel DB senza cancellarli.
     * Dedup BSSID per le reti; sessioni e record sempre come nuovi.
     */
    fun onImportClick(uri: Uri, formato: ExportFormat) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            _importState.value = ImportState.Loading
            try {
                val contentResolver = getApplication<Application>().contentResolver
                val cacheDir = getApplication<Application>().cacheDir

                val input = contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException(app.getString(R.string.import_error_open_selected_file))

                val bundle = SerializerFactory.get(formato).import(input, cacheDir)

                repo.importMergeBundle(bundle, db)

                _importState.value = ImportState.Success
            } catch (e: Exception) {
                // Cleanup file temp residui in caso di errore
                cleanupTempFiles()
                _importState.value = ImportState.Error(
                    e.message ?: app.getString(R.string.import_error_unknown)
                )
            }
        }
    }

    /**
     * Cancella tutto il contenuto del DB.
     * Ordine: records → sessions → networks (rispetta FK).
     */
    fun onDeleteAllClick() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            try {
                repo.deleteAllRecords()
                repo.deleteAllSessions()
                repo.deleteAllNetworks()
            } catch (e: Exception) {
                _importState.value = ImportState.Error(
                    app.getString(
                        R.string.import_error_delete,
                        e.message ?: app.getString(R.string.common_error_unknown)
                    )
                )
            }
        }
    }

    /** Pulizia preventiva dei file temp in caso di import fallito a metà */
    private fun cleanupTempFiles() {
        val cacheDir = getApplication<Application>().cacheDir
        listOf(
            "import_networks.csv", "import_sessions.csv", "import_records.csv",
            "import_networks.jsonl", "import_sessions.jsonl", "import_records.jsonl"
        ).forEach { java.io.File(cacheDir, it).delete() }
    }

    fun resetExportState() { _exportState.value = ExportState.Idle }
    fun resetImportState() { _importState.value = ImportState.Idle }
}
