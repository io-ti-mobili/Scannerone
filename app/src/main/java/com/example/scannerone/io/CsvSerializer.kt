package com.example.scannerone.io

import com.example.scannerone.entities.ScanSession
import com.example.scannerone.entities.WifiNetwork
import com.example.scannerone.entities.WifiScanRecord
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Serializer CSV.
 *
 * Export: ogni entità non-null viene scritta come CSV direttamente
 *         nel ZipOutputStream sull'OutputStream del file URI.
 *         Zero ByteArray intermedi. In RAM: massimo un oggetto alla volta.
 *
 * Import: unzippa i file CSV in cacheDir (file temp su disco), poi ritorna
 *         Sequence lazy che parsa ogni file riga per riga.
 *         In RAM: massimo ~1 oggetto per volta durante l'insert chunked.
 *         File temp cancellati automaticamente dopo consumo di ogni Sequence.
 */
class CsvSerializer : DataSerializer {

    // ---- Export ----

    override fun export(bundle: ExportBundle, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            bundle.networks?.let { seq ->
                zip.putNextEntry(ZipEntry("networks.csv"))
                ReflectionCsvUtils.writeCsvToStream(seq, zip)
                zip.closeEntry()
            }
            bundle.sessions?.let { seq ->
                zip.putNextEntry(ZipEntry("sessions.csv"))
                ReflectionCsvUtils.writeCsvToStream(seq, zip)
                zip.closeEntry()
            }
            bundle.records?.let { seq ->
                zip.putNextEntry(ZipEntry("records.csv"))
                ReflectionCsvUtils.writeCsvToStream(seq, zip)
                zip.closeEntry()
            }
        }
        // Non chiudiamo output: gestito dal chiamante
    }

    // ---- Import ----

    override fun import(input: InputStream, cacheDir: File): ExportBundle {
        val tempNetworks = File(cacheDir, "import_networks.csv")
        val tempSessions = File(cacheDir, "import_sessions.csv")
        val tempRecords  = File(cacheDir, "import_records.csv")

        // Pulizia preventiva di eventuali file residui
        listOf(tempNetworks, tempSessions, tempRecords).forEach { it.delete() }

        // Fase 1: estrai ogni entry CSV su disco (non in RAM)
        var networksFound = false
        var sessionsFound = false
        var recordsFound  = false

        val zip = ZipInputStream(input)
        var entry = zip.nextEntry
        while (entry != null) {
            val dest = when (entry.name) {
                "networks.csv" -> { networksFound = true; tempNetworks }
                "sessions.csv" -> { sessionsFound = true; tempSessions }
                "records.csv"  -> { recordsFound  = true; tempRecords  }
                else           -> null
            }
            if (dest != null) {
                dest.outputStream().use { out -> zip.copyTo(out) }
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
        zip.close()

        if (!networksFound || !sessionsFound || !recordsFound) {
            listOf(tempNetworks, tempSessions, tempRecords).forEach { it.delete() }
            throw IllegalArgumentException(
                "Bundle incompleto: lo zip deve contenere networks.csv, sessions.csv e records.csv."
            )
        }

        // Validazione upfront: verifica header prima di iniziare qualsiasi operazione sul DB
        try {
            ReflectionCsvUtils.validateCsvHeaders(tempNetworks, WifiNetwork::class)
            ReflectionCsvUtils.validateCsvHeaders(tempSessions, ScanSession::class)
            ReflectionCsvUtils.validateCsvHeaders(tempRecords, WifiScanRecord::class)
        } catch (e: Exception) {
            listOf(tempNetworks, tempSessions, tempRecords).forEach { it.delete() }
            throw IllegalArgumentException(e.message)
        }

        // Fase 2: ritorna Sequence lazy — ogni file viene letto riga per riga
        // e cancellato automaticamente dopo consumo completo
        return ExportBundle(
            networks = ReflectionCsvUtils.parseCsvLazySequence(tempNetworks, WifiNetwork::class),
            sessions = ReflectionCsvUtils.parseCsvLazySequence(tempSessions, ScanSession::class),
            records  = ReflectionCsvUtils.parseCsvLazySequence(tempRecords,  WifiScanRecord::class)
        )
    }
}
