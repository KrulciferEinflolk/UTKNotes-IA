package com.example.data.local.llm

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Sanitizador de metadatos GGUF para compatibilidad con el entorno de ejecución Android.
 *
 * Corrige el problema en el que plantillas de chat extensas (como la de DeepSeek-R1)
 * contienen secuencias de caracteres multibyte UTF-8 no estándar (ej. ＜｜User｜＞, ▁),
 * las cuales al ser leídas y truncadas por los bindings JNI nativos de llama.cpp / rnllama
 * provocan un error fatal irrecuperable en Android ART:
 * "JNI DETECTED ERROR IN APPLICATION: input is not valid Modified UTF-8: illegal continuation byte 0".
 *
 * Esta clase sustituye la plantilla in-place por una plantilla ChatML limpia en ASCII puro
 * sin alterar el tamaño del archivo ni desplazar ningún offset de tensores en el archivo GGUF.
 */
object GgufMetadataSanitizer {
    private const val TAG = "GgufMetadataSanitizer"

    private val CHAT_TEMPLATE_KEY = "tokenizer.chat_template".toByteArray(Charsets.UTF_8)
    private val CLEAN_CHATML_TEMPLATE = (
        "{% for message in messages %}" +
        "{{'<|im_start|>' + message['role'] + '\\n' + message['content'] + '<|im_end|>\\n'}}" +
        "{% endfor %}" +
        "{% if add_generation_prompt %}{{'<|im_start|>assistant\\n'}}{% endif %}"
    ).toByteArray(Charsets.US_ASCII)

    /**
     * Revisa y sanitiza en el archivo GGUF el valor de `tokenizer.chat_template`
     * para garantizar que contenga únicamente caracteres ASCII válidos en Modified UTF-8.
     * Retorna true si se encontró y sanitizó con éxito, false si no fue necesario o no se encontró.
     */
    fun sanitizeChatTemplate(file: File): Boolean {
        if (!file.exists() || !file.canRead() || !file.canWrite()) {
            return false
        }
        var raf: RandomAccessFile? = null
        try {
            raf = RandomAccessFile(file, "rw")
            // Los metadatos de cabecera de GGUF residen siempre en los primeros 10 MB del archivo
            val maxScan = minOf(file.length(), 10L * 1024 * 1024).toInt()
            if (maxScan < 64) return false

            val buffer = ByteArray(maxScan)
            raf.seek(0)
            val bytesRead = raf.read(buffer)
            if (bytesRead < 64) return false

            // Verificar número mágico GGUF (0x46554747 = "GGUF")
            if (buffer[0] != 'G'.code.toByte() || buffer[1] != 'G'.code.toByte() ||
                buffer[2] != 'U'.code.toByte() || buffer[3] != 'F'.code.toByte()) {
                return false
            }

            // Construir la aguja de búsqueda en little-endian:
            // uint64 (23L) + "tokenizer.chat_template" + uint32 (8 = GGUF_METADATA_VALUE_TYPE_STRING)
            val needle = ByteBuffer.allocate(8 + CHAT_TEMPLATE_KEY.size + 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putLong(CHAT_TEMPLATE_KEY.size.toLong())
                .put(CHAT_TEMPLATE_KEY)
                .putInt(8) // Type STRING
                .array()

            val pos = indexOf(buffer, needle, 0, bytesRead)
            if (pos == -1) {
                return false
            }

            val valLenOffset = pos + needle.size
            if (valLenOffset + 8 > bytesRead) return false

            val valLenBuffer = ByteBuffer.wrap(buffer, valLenOffset, 8).order(ByteOrder.LITTLE_ENDIAN)
            val valLen = valLenBuffer.long
            if (valLen <= 0 || valLen > 200_000L) return false

            val valOffset = valLenOffset + 8
            if (valOffset + valLen > bytesRead) {
                return false
            }

            // Comprobar si contiene caracteres no-ASCII (> 127) o bytes 0 no esperados
            var hasProblematicBytes = false
            for (i in 0 until valLen.toInt()) {
                val b = buffer[valOffset + i].toInt() and 0xFF
                if (b > 127 || b == 0) {
                    hasProblematicBytes = true
                    break
                }
            }

            if (!hasProblematicBytes) {
                // Ya es completamente seguro para JNI
                return false
            }

            Log.w(TAG, "Detectada plantilla con caracteres multibyte en ${file.name}. Sanitizando para evitar fallo JNI SIGABRT...")

            // Escribir la plantilla limpia ChatML en ASCII y rellenar con espacios (0x20)
            // de modo que la longitud exacta del valor no cambie y ningún offset de tensores se desplace.
            raf.seek(valOffset.toLong())
            val intValLen = valLen.toInt()
            if (intValLen >= CLEAN_CHATML_TEMPLATE.size) {
                raf.write(CLEAN_CHATML_TEMPLATE)
                val paddingSize = intValLen - CLEAN_CHATML_TEMPLATE.size
                if (paddingSize > 0) {
                    val pad = ByteArray(minOf(paddingSize, 4096)) { 0x20.toByte() }
                    var remaining = paddingSize
                    while (remaining > 0) {
                        val toWrite = minOf(remaining, pad.size)
                        raf.write(pad, 0, toWrite)
                        remaining -= toWrite
                    }
                }
            } else {
                val sanitizedBytes = ByteArray(intValLen)
                for (i in 0 until intValLen) {
                    val b = buffer[valOffset + i].toInt() and 0xFF
                    sanitizedBytes[i] = if (b > 127 || b == 0) 0x20.toByte() else buffer[valOffset + i]
                }
                raf.write(sanitizedBytes)
            }

            Log.i(TAG, "Plantilla GGUF de ${file.name} sanitizada exitosamente a formato ASCII seguro.")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Excepción durante la sanitización de ${file.name}", e)
            return false
        } finally {
            try {
                raf?.close()
            } catch (_: Exception) {}
        }
    }

    private fun indexOf(source: ByteArray, target: ByteArray, start: Int, end: Int): Int {
        if (target.isEmpty() || end - start < target.size) return -1
        outer@ for (i in start..(end - target.size)) {
            for (j in target.indices) {
                if (source[i + j] != target[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
