package com.example.scannerone.io

/**
 * Stato dell'operazione di export, osservato dalla UI.
 */
sealed class ExportState {
    object Idle : ExportState()
    object Loading : ExportState()
    object Success : ExportState() // File già scritto sull'OutputStream — niente ByteArray
    data class Error(val message: String?) : ExportState()
}

/**
 * Stato dell'operazione di import, osservato dalla UI.
 */
sealed class ImportState {
    object Idle : ImportState()
    object Loading : ImportState()
    object Success : ImportState()
    data class Error(val message: String?) : ImportState()
}
