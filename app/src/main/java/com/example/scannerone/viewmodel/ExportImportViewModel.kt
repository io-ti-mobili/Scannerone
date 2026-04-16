package com.example.scannerone.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.scannerone.database.AppDatabase
import com.example.scannerone.io.ExportBundle
import com.example.scannerone.io.ExportFormat
import com.example.scannerone.io.ExportSelection
import com.example.scannerone.io.ExportState
import com.example.scannerone.io.ImportState
import com.example.scannerone.io.SerializerFactory
import com.example.scannerone.repository.WifiScanRepository
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

    private val repo = WifiScanRepository(
        AppDatabase.getDatabase(application).wifiScanDao()
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
                } ?: throw IllegalStateException("Impossibile aprire il file di destinazione")

                _exportState.value = ExportState.Success
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.message ?: "Errore sconosciuto durante l'export")
            }
        }
    }

    /**
     * Import streaming dall'URI scelto dall'utente.
     * Per CSV: estrae entries zip in cacheDir (file temp su disco), poi parsa lazy.
     * Per JSON: estrae array in file JSONL temp su cacheDir, poi parsa lazy.
     * In entrambi i casi: al massimo un chunk da 200 oggetti in RAM durante l'insert.
     * File temp cancellati automaticamente dopo il consumo di ogni Sequence.
     */
    fun onImportClick(uri: Uri, formato: ExportFormat) {
        viewModelScope.launch(Dispatchers.IO) {
            _importState.value = ImportState.Loading
            try {
                val contentResolver = getApplication<Application>().contentResolver
                val cacheDir = getApplication<Application>().cacheDir

                val input = contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Impossibile aprire il file selezionato")

                val bundle = SerializerFactory.get(formato).import(input, cacheDir)

                val db = AppDatabase.getDatabase(getApplication())
                repo.importFullBundleAtomic(bundle, db)

                _importState.value = ImportState.Success
            } catch (e: Exception) {
                // Cleanup file temp residui in caso di errore
                cleanupTempFiles()
                _importState.value = ImportState.Error(e.message ?: "Errore sconosciuto durante l'import")
            }
        }
    }

    /**
     * Cancella tutto il contenuto del DB.
     * Ordine: records → sessions → networks (rispetta FK).
     */
    fun onDeleteAllClick() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.deleteAllRecords()
                repo.deleteAllSessions()
                repo.deleteAllNetworks()
            } catch (e: Exception) {
                _importState.value = ImportState.Error("Errore nella cancellazione: ${e.message}")
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
