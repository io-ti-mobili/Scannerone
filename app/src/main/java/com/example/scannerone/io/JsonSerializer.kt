package com.example.scannerone.io

import com.example.scannerone.entities.ScanSession
import com.example.scannerone.entities.WifiNetwork
import com.example.scannerone.entities.WifiScanRecord
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.gson.stream.JsonWriter
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Serializer JSON.
 *
 * Export: Gson streaming JsonWriter → scrive direttamente sull'OutputStream.
 *         Zero ByteArray intermedi. In RAM: massimo un oggetto alla volta.
 *
 * Import: Jackson streaming parser → estrae ogni array in un file JSONL temp
 *         (una riga = un oggetto JSON serializzato). Ritorna Sequence lazy che
 *         parsa ogni riga on-demand. File temp cancellati dopo consumo.
 */
class JsonSerializer : DataSerializer {

    private val gson = com.google.gson.GsonBuilder().create()
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.INDENT_OUTPUT)

    // ---- Export ----

    override fun export(bundle: ExportBundle, output: OutputStream) {
        val writer = JsonWriter(output.bufferedWriter(Charsets.UTF_8))
        writer.beginObject()

        bundle.networks?.let { seq ->
            writer.name("networks")
            writer.beginArray()
            seq.forEach { gson.toJson(it, WifiNetwork::class.java, writer) }
            writer.endArray()
        }
        bundle.sessions?.let { seq ->
            writer.name("sessions")
            writer.beginArray()
            seq.forEach { gson.toJson(it, ScanSession::class.java, writer) }
            writer.endArray()
        }
        bundle.records?.let { seq ->
            writer.name("records")
            writer.beginArray()
            seq.forEach { gson.toJson(it, WifiScanRecord::class.java, writer) }
            writer.endArray()
        }

        writer.endObject()
        writer.flush()
        // Non chiudiamo il writer: l'OutputStream è gestito dal chiamante
    }

    // ---- Import ----

    override fun import(input: InputStream, cacheDir: File): ExportBundle {
        val tempNetworks = File(cacheDir, "import_networks.jsonl")
        val tempSessions = File(cacheDir, "import_sessions.jsonl")
        val tempRecords  = File(cacheDir, "import_records.jsonl")

        // Pulizia preventiva di eventuali file residui
        listOf(tempNetworks, tempSessions, tempRecords).forEach { it.delete() }

        // Single-pass streaming: legge i token JSON e scarica ogni array
        // in un file JSONL (una riga = un oggetto serializzato minimale)
        val parser = JsonFactory().createParser(input)
        var networksFound = false
        var sessionsFound = false
        var recordsFound  = false

        while (parser.nextToken() != null) {
            if (parser.currentToken == JsonToken.FIELD_NAME) {
                when (parser.currentName()) {
                    "networks" -> {
                        parser.nextToken() // START_ARRAY
                        writeJsonlTemp(parser, tempNetworks, WifiNetwork::class.java)
                        networksFound = true
                    }
                    "sessions" -> {
                        parser.nextToken()
                        writeJsonlTemp(parser, tempSessions, ScanSession::class.java)
                        sessionsFound = true
                    }
                    "records" -> {
                        parser.nextToken()
                        writeJsonlTemp(parser, tempRecords, WifiScanRecord::class.java)
                        recordsFound = true
                    }
                }
            }
        }
        parser.close()

        if (!networksFound || !sessionsFound || !recordsFound) {
            listOf(tempNetworks, tempSessions, tempRecords).forEach { it.delete() }
            throw IllegalArgumentException(
                "Bundle incompleto: il file JSON deve contenere networks, sessions e records."
            )
        }

        // Ritorna Sequence lazy: ogni elemento viene parsato e rilasciato subito
        return ExportBundle(
            networks = lazyJsonlSequence(tempNetworks, WifiNetwork::class.java),
            sessions = lazyJsonlSequence(tempSessions, ScanSession::class.java),
            records  = lazyJsonlSequence(tempRecords,  WifiScanRecord::class.java)
        )
    }

    /**
     * Consuma un array JSON dallo streaming parser e scrive ogni oggetto
     * come riga JSON minimale (JSONL) nel file temp.
     * In RAM: massimo un oggetto deserializzato per volta.
     */
    private fun <T> writeJsonlTemp(
        parser: com.fasterxml.jackson.core.JsonParser,
        file: File,
        clazz: Class<T>
    ) {
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                val obj = objectMapper.readValue(parser, clazz)
                writer.write(objectMapper.writeValueAsString(obj))
                writer.newLine()
            }
        }
    }

    /**
     * Sequence lazy: parsa ogni riga del file JSONL on-demand.
     * File cancellato automaticamente dopo consumo completo.
     */
    private fun <T> lazyJsonlSequence(file: File, clazz: Class<T>): Sequence<T> = sequence {
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.lineSequence()
                .filter { it.isNotBlank() }
                .forEach { line ->
                    yield(objectMapper.readValue(line, clazz))
                }
        }
        file.delete()
    }
}
