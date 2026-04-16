package com.example.scannerone.io

import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Strategy pattern: interfaccia comune per tutti i formati di serializzazione.
 *
 * Export: scrive direttamente sull'OutputStream — nessun ByteArray intermedio in RAM.
 * Import: legge dall'InputStream usando file temp su disco (cacheDir) per il parsing lazy.
 */
interface DataSerializer {
    /**
     * Serializza il bundle direttamente sull'OutputStream (es: URI file picker).
     * Le Sequence vengono iterate chunk per chunk durante la scrittura.
     */
    fun export(bundle: ExportBundle, output: OutputStream)

    /**
     * Deserializza l'InputStream in un ExportBundle con Sequence lazy.
     * I serializer che ne hanno bisogno (es. CSV) usano cacheDir per file temp.
     * I file temp vengono cancellati automaticamente dopo che ogni Sequence è esaurita.
     */
    fun import(input: InputStream, cacheDir: File): ExportBundle
}
