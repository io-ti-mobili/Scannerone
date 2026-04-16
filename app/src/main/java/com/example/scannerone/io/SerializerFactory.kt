package com.example.scannerone.io

/**
 * Factory pattern: ritorna il serializer giusto in base al formato scelto dall'utente.
 */
object SerializerFactory {
    fun get(format: ExportFormat): DataSerializer = when (format) {
        ExportFormat.JSON -> JsonSerializer()
        ExportFormat.CSV  -> CsvSerializer()
    }
}
