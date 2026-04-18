package com.example.scannerone.io

import java.io.BufferedReader
import java.io.File
import java.io.OutputStream
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

/**
 * Utility per conversione data class ↔ CSV via reflection.
 * Le colonne vengono generate automaticamente dai campi della data class.
 * Supporta escaping RFC 4180 (virgole, newline, doppi apici nei valori).
 */
object ReflectionCsvUtils {

    /**
     * Scrive una Sequence di data class su un OutputStream CSV.
     * Streaming puro: in RAM c'è al massimo un elemento alla volta.
     */
    inline fun <reified T : Any> writeCsvToStream(items: Sequence<T>, output: OutputStream) {
        val kClass = T::class
        val params = kClass.primaryConstructor?.parameters
            ?: error("${kClass.simpleName} non ha un primary constructor")
        val props = kClass.memberProperties.associateBy { it.name }

        // Wrapper non-chiudente: BufferedWriter.close() flushа e chiude questo wrapper,
        // ma NON propaga la chiusura al ZipOutputStream sottostante.
        val nonClosingOutput = object : java.io.FilterOutputStream(output) {
            override fun close() { /* intentionally empty — chiude il chiamante */ }
        }

        nonClosingOutput.bufferedWriter(Charsets.UTF_8).use { writer ->
            // Header
            writer.write(params.joinToString(",") { it.name!! })
            writer.newLine()

            // Righe
            items.forEach { item ->
                val row = params.joinToString(",") { param ->
                    val value = props[param.name]?.get(item)
                    escapeCsvValue(value?.toString() ?: "")
                }
                writer.write(row)
                writer.newLine()
            }
        }
        // BufferedWriter.close() =  flush + chiude il wrapper (no-op) → ZipOutputStream intatto
    }

    /**
     * Parsa un InputStream CSV in una List di data class.
     * Usato per leggere BufferedReader di ZipInputStream entry per entry.
     */
    inline fun <reified T : Any> parseCsvToList(reader: BufferedReader): List<T> {
        val kClass = T::class
        val constructor = kClass.primaryConstructor
            ?: error("${kClass.simpleName} non ha un primary constructor")
        val headerLine = reader.readLine() ?: return emptyList()
        val headers = parseCsvLine(headerLine)

        val result = mutableListOf<T>()
        var line = reader.readLine()
        while (line != null) {
            if (line.isNotBlank()) {
                val values = parseCsvLine(line)
                result.add(mapToDataClass(constructor, headers, values))
            }
            line = reader.readLine()
        }
        return result
    }

    /**
     * Ritorna una Sequence lazy che parsa un file CSV riga per riga.
     * Il file viene cancellato automaticamente dopo che la Sequence è esaurita.
     * In RAM: al massimo un oggetto alla volta.
     */
    fun <T : Any> parseCsvLazySequence(file: File, kClass: KClass<T>): Sequence<T> = sequence {
        val constructor = kClass.primaryConstructor
            ?: error("${kClass.simpleName} non ha un primary constructor")

        file.bufferedReader(Charsets.UTF_8).use { reader ->
            val headerLine = reader.readLine() ?: return@use
            val headers = parseCsvLine(headerLine)
            var line = reader.readLine()
            while (line != null) {
                if (line.isNotBlank()) {
                    val values = parseCsvLine(line)
                    yield(mapToDataClass(constructor, headers, values))
                }
                line = reader.readLine()
            }
        }
        file.delete() // cleanup automatico dopo consumo completo
    }

    /**
     * Crea un'istanza della data class mappando header CSV → parametri del constructor.
     */
    fun <T : Any> mapToDataClass(
        constructor: kotlin.reflect.KFunction<T>,
        headers: List<String>,
        values: List<String>
    ): T {
        val headerToValue = headers.zip(values).toMap()
        val args = mutableMapOf<KParameter, Any?>()

        for (param in constructor.parameters) {
            val rawValue = headerToValue[param.name]
            if (rawValue == null || rawValue.isBlank() || rawValue == "null") {
                if (param.isOptional) continue
                if (param.type.isMarkedNullable) {
                    args[param] = null
                    continue
                }
                error("Campo obbligatorio '${param.name}' mancante nel CSV")
            }
            args[param] = convertValue(rawValue, param)
        }
        return constructor.callBy(args)
    }

    /**
     * Converte una stringa CSV nel tipo corretto basandosi sul KParameter.
     */
    private fun convertValue(value: String, param: KParameter): Any {
        val typeName = param.type.toString().removeSuffix("?")
        return when (typeName) {
            "kotlin.Int", "Int"         -> value.toInt()
            "kotlin.Long", "Long"       -> value.toLong()
            "kotlin.Double", "Double"   -> value.toDouble()
            "kotlin.Float", "Float"     -> value.toFloat()
            "kotlin.Boolean", "Boolean" -> value.toBoolean()
            "kotlin.String", "String"   -> value
            else                        -> value
        }
    }

    /**
     * Escaping RFC 4180: se il valore contiene virgole, newline o doppi apici,
     * viene racchiuso tra doppi apici con escape dei doppi apici interni.
     */
    fun escapeCsvValue(value: String): String {
        return if (value.contains(',') || value.contains('\n') || value.contains('"')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    /**
     * Parser CSV che gestisce valori tra doppi apici (RFC 4180).
     */
    fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuotes -> inQuotes = true
                c == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    /**
     * Legge solo la prima riga del CSV e verifica che tutti i campi obbligatori
     * del costruttore siano presenti negli header. Fallisce subito se invalido.
     */
    fun <T : Any> validateCsvHeaders(file: File, kClass: kotlin.reflect.KClass<T>) {
        val constructor = kClass.primaryConstructor
            ?: error("${kClass.simpleName} non ha un primary constructor")
        
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            val headerLine = reader.readLine()
                ?: error("File CSV completamente vuoto per ${kClass.simpleName}")
            val headers = parseCsvLine(headerLine)
            
            for (param in constructor.parameters) {
                // Se non è opzionale (=) e non è nullable (?) → è obbligatorio
                if (!param.isOptional && !param.type.isMarkedNullable) {
                    if (!headers.contains(param.name)) {
                        error("CSV invalido per ${kClass.simpleName}: manca colonna obbligatoria '${param.name}'")
                    }
                }
            }
        }
    }
}

