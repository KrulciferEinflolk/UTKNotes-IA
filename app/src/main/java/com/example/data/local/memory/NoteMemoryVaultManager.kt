package com.example.data.local.memory

import android.content.Context
import android.util.Log
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@JsonClass(generateAdapter = true)
data class NoteMemoryBank(
    val noteId: String,
    val title: String = "",
    val summary: String = "",
    val keyFacts: List<String> = emptyList(),
    val topics: List<String> = emptyList(),
    val customContext: String = "",
    val lastUpdated: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class NoteChatMessage(
    val role: String, // "user", "assistant", "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class NoteChatRecord(
    val chatId: String,
    val noteId: String,
    val title: String = "Conversación",
    val messages: List<NoteChatMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class NoteMetadata(
    val noteId: String,
    val title: String,
    val pageId: String,
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Gestor del Banco de Memoria y almacenamiento estructurado por nota.
 * Estructura de carpetas:
 * context.filesDir/notes_vault/note_{noteId}/
 *   ├── metadata.json
 *   ├── memory_bank.json
 *   └── chats/
 *       ├── chat_{chatId_1}.json
 *       └── chat_{chatId_2}.json
 */
class NoteMemoryVaultManager(private val context: Context) {

    private val tag = "NoteMemoryVault"
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val memoryBankAdapter = moshi.adapter(NoteMemoryBank::class.java)
    private val chatRecordAdapter = moshi.adapter(NoteChatRecord::class.java)
    private val metadataAdapter = moshi.adapter(NoteMetadata::class.java)

    private val vaultRoot: File
        get() = File(context.filesDir, "notes_vault").apply { if (!exists()) mkdirs() }

    fun getNoteFolder(noteId: String): File {
        val safeId = noteId.replace("[^a-zA-Z0-9-_]".toRegex(), "_")
        return File(vaultRoot, "note_$safeId").apply { if (!exists()) mkdirs() }
    }

    fun getChatsFolder(noteId: String): File {
        return File(getNoteFolder(noteId), "chats").apply { if (!exists()) mkdirs() }
    }

    suspend fun saveMetadata(metadata: NoteMetadata) = withContext(Dispatchers.IO) {
        try {
            val folder = getNoteFolder(metadata.noteId)
            val file = File(folder, "metadata.json")
            val json = metadataAdapter.toJson(metadata)
            file.writeText(json)
        } catch (e: Exception) {
            Log.e(tag, "Error saving metadata for note ${metadata.noteId}", e)
        }
    }

    suspend fun getMetadata(noteId: String): NoteMetadata? = withContext(Dispatchers.IO) {
        try {
            val file = File(getNoteFolder(noteId), "metadata.json")
            if (file.exists()) {
                val json = file.readText()
                metadataAdapter.fromJson(json)
            } else null
        } catch (e: Exception) {
            Log.e(tag, "Error reading metadata for note $noteId", e)
            null
        }
    }

    suspend fun saveMemoryBank(memoryBank: NoteMemoryBank) = withContext(Dispatchers.IO) {
        try {
            val folder = getNoteFolder(memoryBank.noteId)
            val file = File(folder, "memory_bank.json")
            val json = memoryBankAdapter.toJson(memoryBank)
            file.writeText(json)
        } catch (e: Exception) {
            Log.e(tag, "Error saving memory bank for note ${memoryBank.noteId}", e)
        }
    }

    suspend fun getMemoryBank(noteId: String): NoteMemoryBank = withContext(Dispatchers.IO) {
        try {
            val file = File(getNoteFolder(noteId), "memory_bank.json")
            if (file.exists()) {
                val json = file.readText()
                memoryBankAdapter.fromJson(json) ?: NoteMemoryBank(noteId = noteId)
            } else {
                NoteMemoryBank(noteId = noteId)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error reading memory bank for note $noteId", e)
            NoteMemoryBank(noteId = noteId)
        }
    }

    suspend fun addKeyFact(noteId: String, fact: String) = withContext(Dispatchers.IO) {
        val current = getMemoryBank(noteId)
        if (!current.keyFacts.contains(fact)) {
            val updated = current.copy(
                keyFacts = current.keyFacts + fact,
                lastUpdated = System.currentTimeMillis()
            )
            saveMemoryBank(updated)
        }
    }

    suspend fun updateSummary(noteId: String, summary: String, topics: List<String> = emptyList()) = withContext(Dispatchers.IO) {
        val current = getMemoryBank(noteId)
        val updated = current.copy(
            summary = summary,
            topics = if (topics.isNotEmpty()) topics else current.topics,
            lastUpdated = System.currentTimeMillis()
        )
        saveMemoryBank(updated)
    }

    suspend fun saveChat(chatRecord: NoteChatRecord) = withContext(Dispatchers.IO) {
        try {
            val chatsFolder = getChatsFolder(chatRecord.noteId)
            val safeChatId = chatRecord.chatId.replace("[^a-zA-Z0-9-_]".toRegex(), "_")
            val file = File(chatsFolder, "chat_$safeChatId.json")
            val json = chatRecordAdapter.toJson(chatRecord)
            file.writeText(json)
        } catch (e: Exception) {
            Log.e(tag, "Error saving chat ${chatRecord.chatId} for note ${chatRecord.noteId}", e)
        }
    }

    suspend fun getChat(noteId: String, chatId: String): NoteChatRecord? = withContext(Dispatchers.IO) {
        try {
            val chatsFolder = getChatsFolder(noteId)
            val safeChatId = chatId.replace("[^a-zA-Z0-9-_]".toRegex(), "_")
            val file = File(chatsFolder, "chat_$safeChatId.json")
            if (file.exists()) {
                val json = file.readText()
                chatRecordAdapter.fromJson(json)
            } else null
        } catch (e: Exception) {
            Log.e(tag, "Error reading chat $chatId for note $noteId", e)
            null
        }
    }

    suspend fun listChatsForNote(noteId: String): List<NoteChatRecord> = withContext(Dispatchers.IO) {
        try {
            val chatsFolder = getChatsFolder(noteId)
            chatsFolder.listFiles { file -> file.name.startsWith("chat_") && file.name.endsWith(".json") }
                ?.mapNotNull { file ->
                    try {
                        chatRecordAdapter.fromJson(file.readText())
                    } catch (e: Exception) {
                        null
                    }
                }
                ?.sortedByDescending { it.updatedAt }
                ?: emptyList()
        } catch (e: Exception) {
            Log.e(tag, "Error listing chats for note $noteId", e)
            emptyList()
        }
    }

    /**
     * Genera un bloque de contexto compacto para alimentar a modelos con ventana moderada (ej. Qwen 1.5B).
     * En lugar de meter 30 páginas, inyecta el resumen y los hechos atómicos del Banco de Memoria.
     */
    suspend fun buildAugmentedSystemPrompt(noteId: String, noteTitle: String): String = withContext(Dispatchers.IO) {
        val bank = getMemoryBank(noteId)
        val sb = StringBuilder()
        sb.append("Eres un asistente inteligente para la nota \"$noteTitle\". Responde con claridad, precisión y en español.\n\n")
        
        if (bank.summary.isNotBlank()) {
            sb.append("=== BANCO DE MEMORIA (RESUMEN EJECUTIVO) ===\n")
            sb.append(bank.summary.trim()).append("\n\n")
        }

        if (bank.keyFacts.isNotEmpty()) {
            sb.append("=== HECHOS CLAVE ATÓMICOS ===\n")
            bank.keyFacts.take(15).forEach { fact ->
                sb.append("• ").append(fact).append("\n")
            }
            sb.append("\n")
        }

        if (bank.topics.isNotEmpty()) {
            sb.append("Temas clave: ").append(bank.topics.joinToString(", ")).append("\n\n")
        }

        sb.append("Utiliza esta memoria para responder con exactitud sin inventar información no presente.")
        sb.toString()
    }
}
