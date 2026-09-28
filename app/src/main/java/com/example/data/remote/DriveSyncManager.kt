package com.example.data.remote

import android.content.Context
import android.util.Log
import android.net.Uri
import android.provider.OpenableColumns
import java.util.UUID
import com.example.data.local.AppDatabase
import com.example.data.model.BookEntity
import com.example.data.model.NoteEntity
import com.example.data.model.PageEntity
import com.example.data.model.ChatSessionEntity
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import com.google.android.gms.auth.GoogleAuthUtil
import android.accounts.Account
import android.accounts.AccountManager
import com.example.data.local.memory.NoteMemoryVaultManager
import com.example.data.local.memory.NoteMemoryBank
import com.example.data.local.memory.NoteChatRecord
import com.example.data.local.memory.NoteMetadata

@JsonClass(generateAdapter = true)
data class DriveBackupPayload(
    val books: List<BookEntity>,
    val pages: List<PageEntity>,
    val notes: List<NoteEntity>,
    val chatSessions: List<ChatSessionEntity> = emptyList(),
    val apiKey: String? = null,
    val timestamp: Long
)

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Success(val message: String, val timestamp: Long) : SyncState()
    data class Error(val message: String, val timestamp: Long = System.currentTimeMillis()) : SyncState()
}

class DriveSyncManager(
    private val context: Context,
    private val database: AppDatabase
) {
    private val prefs = context.getSharedPreferences("utk_notes_prefs", Context.MODE_PRIVATE)
    private val memoryVault = NoteMemoryVaultManager(context)

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail

    private val _recoveryIntent = MutableStateFlow<android.content.Intent?>(null)
    val recoveryIntent: StateFlow<android.content.Intent?> = _recoveryIntent

    fun clearRecoveryIntent() {
        _recoveryIntent.value = null
    }

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient()

    init {
        // Clear any legacy API keys from preferences
        prefs.edit().apply {
            prefs.all.keys.filter { it.contains("gemini") || it.contains("api_key") }.forEach { remove(it) }
        }.apply()

        val savedEmail = prefs.getString("google_email", null)
        if (savedEmail != null) {
            _userEmail.value = savedEmail
            _isConnected.value = true
        } else {
            _userEmail.value = null
            _isConnected.value = false
        }
    }

    fun getPrimaryGoogleAccount(): String? {
        try {
            val am = AccountManager.get(context)
            val accounts = am.getAccountsByType("com.google")
            if (accounts.isNotEmpty()) {
                return accounts[0].name
            }
        } catch (e: Exception) {
            Log.e("DriveSyncManager", "Error getting accounts from AccountManager", e)
        }
        return null
    }

    fun isAutoLoginDisabled(): Boolean {
        return prefs.getBoolean("auto_login_disabled", false)
    }

    fun connectDrive(email: String) {
        prefs.edit()
            .putBoolean("auto_login_disabled", false)
            .putString("google_email", email)
            .apply()
        _userEmail.value = email
        _isConnected.value = true
        if (email == "offline") {
            _syncState.value = SyncState.Success("Iniciado en Modo Local (offline)", System.currentTimeMillis())
        } else {
            _syncState.value = SyncState.Success("Conectado con $email", System.currentTimeMillis())
        }
    }

    fun disconnectDrive() {
        prefs.edit()
            .remove("google_email")
            .putBoolean("auto_login_disabled", true)
            .apply()
        _userEmail.value = null
        _isConnected.value = false
        _syncState.value = SyncState.Idle
    }

    /**
     * Performs active two-way synchronization between local Room DB and Google Drive.
     * Restores all books, pages, notes, multimedia metadata, chat agent history, and custom API key.
     */
    suspend fun synchronize(): Unit = withContext(Dispatchers.IO) {
        val email = _userEmail.value
        if (email.isNullOrEmpty()) {
            _syncState.value = SyncState.Error("No se encontró correo asociado.")
            return@withContext
        }

        _syncState.value = SyncState.Syncing

        try {
            Log.d("DriveSyncManager", "Iniciando sincronización para: $email")
            val dao = database.notesDao()

            // Automatically migrate any offline books, pages, notes, and chat sessions to this email
            if (email != "offline") {
                try {
                    dao.migrateOfflineBooks(email)
                    dao.migrateOfflinePages(email)
                    dao.migrateOfflineNotes(email)
                    dao.migrateOfflineChatSessions(email)
                } catch (mEx: Exception) {
                    Log.e("DriveSyncManager", "Error durante migración de datos locales", mEx)
                }
            }

            // 1. Proactive Local Restoration: If the local database is empty for this email,
            // try to restore from a previously stored local backup file (prevents data loss on reinstall)
            val localBackupFile = File(context.getExternalFilesDir(null), "utk_notes_backup_${email}.json")
            val existingBooks = dao.getAllBooks(email)
            if (existingBooks.isEmpty() && localBackupFile.exists()) {
                try {
                    val localContent = localBackupFile.readText()
                    if (localContent.isNotEmpty()) {
                        val jsonAdapter = moshi.adapter(DriveBackupPayload::class.java)
                        val localPayload = jsonAdapter.fromJson(localContent)
                        if (localPayload != null) {
                            Log.d("DriveSyncManager", "Restaurando copia local para evitar pérdida de datos...")
                            mergeBackup(localPayload, email)
                        }
                    }
                } catch (ex: Exception) {
                    Log.e("DriveSyncManager", "Error al restaurar respaldo local inicial", ex)
                }
            }

            var token: String? = null
            if (email != "offline") {
                // Fetch OAuth Access Token from Google Play Services
                val account = Account(email, "com.google")
                val primaryScope = "oauth2:https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive.appdata"
                val fallbackScope = "oauth2:https://www.googleapis.com/auth/drive.appdata"
                try {
                    token = GoogleAuthUtil.getToken(context, account, primaryScope)
                    Log.d("DriveSyncManager", "OAuth Token con permisos de Drive y AppData obtenido correctamente")
                } catch (recoverable: com.google.android.gms.auth.UserRecoverableAuthException) {
                    Log.w("DriveSyncManager", "Se requiere autorización del usuario para acceder a Drive", recoverable)
                    _recoveryIntent.value = recoverable.intent
                    _syncState.value = SyncState.Error("Se requiere permiso para acceder a Google Drive. Por favor concede la autorización.")
                    return@withContext
                } catch (authEx: Exception) {
                    Log.w("DriveSyncManager", "Intentando con scope de fallback para $email: ${authEx.localizedMessage}")
                    try {
                        token = GoogleAuthUtil.getToken(context, account, fallbackScope)
                    } catch (recoverable2: com.google.android.gms.auth.UserRecoverableAuthException) {
                        _recoveryIntent.value = recoverable2.intent
                        _syncState.value = SyncState.Error("Se requiere autorización para Google Drive.")
                        return@withContext
                    } catch (authEx2: Exception) {
                        Log.e("DriveSyncManager", "No se pudo obtener token de Google para $email", authEx2)
                        _syncState.value = SyncState.Error("No se pudo autenticar con Google: ${authEx2.localizedMessage}")
                        return@withContext
                    }
                }
            }

            if (token != null && email != "offline") {
                // --- ONLINE GOOGLE DRIVE SYNC ---
                val searchUrl = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder"
                val searchRequest = Request.Builder()
                    .url(searchUrl)
                    .header("Authorization", "Bearer $token")
                    .build()

                var fileId: String? = null
                client.newCall(searchRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = JSONObject(body)
                        val files = json.optJSONArray("files")
                        if (files != null) {
                            for (i in 0 until files.length()) {
                                val fileObj = files.getJSONObject(i)
                                if (fileObj.optString("name") == "utk_notes_ia_backup.json") {
                                    fileId = fileObj.optString("id")
                                    Log.d("DriveSyncManager", "Archivo de respaldo encontrado en Drive: $fileId")
                                    break
                                }
                            }
                        }
                    } else if (response.code == 401 || response.code == 403) {
                        GoogleAuthUtil.clearToken(context, token)
                        Log.e("DriveSyncManager", "Token expirado o sin permisos, limpiado.")
                        throw Exception("Permisos de Drive inválidos o expirados. Intenta sincronizar de nuevo.")
                    } else {
                        Log.e("DriveSyncManager", "Búsqueda en Drive falló: ${response.code}")
                    }
                }

                // If backup file exists on Drive, download and merge it
                if (fileId != null) {
                    val downloadUrl = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
                    val downloadRequest = Request.Builder()
                        .url(downloadUrl)
                        .header("Authorization", "Bearer $token")
                        .build()

                    client.newCall(downloadRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            val content = response.body?.string()
                            if (!content.isNullOrEmpty()) {
                                try {
                                    val jsonAdapter = moshi.adapter(DriveBackupPayload::class.java)
                                    val remoteBackup = jsonAdapter.fromJson(content)
                                    if (remoteBackup != null) {
                                        Log.d("DriveSyncManager", "Mergeando datos remotos de Drive...")
                                        mergeBackup(remoteBackup, email)
                                    }
                                } catch (parseEx: Exception) {
                                    Log.e("DriveSyncManager", "Error parseando backup de Drive", parseEx)
                                }
                            }
                        } else {
                            Log.e("DriveSyncManager", "Descarga de backup falló: ${response.code}")
                        }
                    }
                }

                // Fetch final merged local data for this specific email to upload
                val books = dao.getAllBooks(email)
                val pages = mutableListOf<PageEntity>()
                for (book in books) {
                    pages.addAll(dao.getPagesForBook(book.id))
                }
                
                // Process ALL notes owned by this email so attachments (images, drawings, audio, video) are uploaded
                val rawNotes = dao.getAllNotes(email)
                val notes = mutableListOf<NoteEntity>()
                for (n in rawNotes) {
                    val updatedNote = uploadAttachmentsForNote(n)
                    if (updatedNote.content != n.content) {
                        dao.insertNote(updatedNote)
                        notes.add(updatedNote)
                    } else {
                        notes.add(n)
                    }
                }
                val chatSessions = dao.getAllChatSessions(email)

                val payload = DriveBackupPayload(
                    books = books,
                    pages = pages,
                    notes = notes,
                    chatSessions = chatSessions,
                    apiKey = null,
                    timestamp = System.currentTimeMillis()
                )

                val jsonAdapter = moshi.adapter(DriveBackupPayload::class.java)
                val jsonString = jsonAdapter.toJson(payload)

                // Save to local device as a copy
                localBackupFile.writeText(jsonString)

                // Upload back to Drive
                if (fileId != null) {
                    val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
                    val mediaType = "application/json".toMediaTypeOrNull()
                    val requestBody = jsonString.toRequestBody(mediaType)
                    
                    val updateRequest = Request.Builder()
                        .url(uploadUrl)
                        .patch(requestBody)
                        .header("Authorization", "Bearer $token")
                        .build()

                    client.newCall(updateRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            Log.d("DriveSyncManager", "Copia de seguridad en Drive actualizada correctamente!")
                        } else {
                            if (response.code == 401 || response.code == 403) {
                                GoogleAuthUtil.clearToken(context, token)
                                throw Exception("Permisos inválidos (actualizando). Token limpiado, intenta nuevamente.")
                            }
                            throw Exception("Error de red al actualizar Drive: ${response.code} ${response.body?.string()}")
                        }
                    }
                } else {
                    // Create new file on Google Drive
                    val metaUrl = "https://www.googleapis.com/drive/v3/files"
                    val metaJson = JSONObject()
                        .put("name", "utk_notes_ia_backup.json")
                        .put("mimeType", "application/json")
                        .put("parents", org.json.JSONArray().put("appDataFolder"))
                    val metaType = "application/json".toMediaTypeOrNull()
                    val metaBody = metaJson.toString().toRequestBody(metaType)

                    val metaRequest = Request.Builder()
                        .url(metaUrl)
                        .post(metaBody)
                        .header("Authorization", "Bearer $token")
                        .build()

                    var createdFileId: String? = null
                    client.newCall(metaRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            val resBody = response.body?.string() ?: ""
                            createdFileId = JSONObject(resBody).optString("id")
                            Log.d("DriveSyncManager", "Nuevo archivo creado en Drive con ID: $createdFileId")
                        } else {
                            if (response.code == 401 || response.code == 403) {
                                GoogleAuthUtil.clearToken(context, token)
                                throw Exception("Permisos inválidos (creando). Token limpiado, intenta nuevamente.")
                            }
                            throw Exception("Error de red creando archivo en Drive: ${response.code} ${response.body?.string()}")
                        }
                    }

                    if (createdFileId != null) {
                        val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$createdFileId?uploadType=media"
                        val mediaType = "application/json".toMediaTypeOrNull()
                        val requestBody = jsonString.toRequestBody(mediaType)
                        
                        val uploadRequest = Request.Builder()
                            .url(uploadUrl)
                            .patch(requestBody)
                            .header("Authorization", "Bearer $token")
                            .build()

                        client.newCall(uploadRequest).execute().use { response ->
                            if (response.isSuccessful) {
                                Log.d("DriveSyncManager", "Nuevo respaldo subido con éxito!")
                            } else {
                                if (response.code == 401 || response.code == 403) {
                                    GoogleAuthUtil.clearToken(context, token)
                                    throw Exception("Permisos inválidos (subiendo). Token limpiado, intenta nuevamente.")
                                }
                                throw Exception("Error de red subiendo contenido a Drive: ${response.code} ${response.body?.string()}")
                            }
                        }
                    }
                }

                // Mark notes as synced locally
                for (note in notes) {
                    if (!note.isSynced) {
                        dao.insertNote(note.copy(isSynced = true))
                    }
                }

                // Create & sync complete hierarchical database structure in Google Drive
                try {
                    syncDatabaseStructureToDrive(token, email, books, pages, notes, chatSessions)
                } catch (structEx: Exception) {
                    Log.w("DriveSyncManager", "Aviso al estructurar carpetas en Drive", structEx)
                }

                val successMsg = "¡Sincronizado con éxito! Base de datos estructurada creada en Google Drive: ${books.size} Libros, ${pages.size} Páginas, ${notes.size} Notas, Banco de Memoria y Chats organizados."
                _syncState.value = SyncState.Success(successMsg, System.currentTimeMillis())
                
                // Save credentials and mark as connected now that restore is complete!
                prefs.edit().putString("google_email", email).apply()
                _isConnected.value = true

            } else {
                // --- OFFLINE/LOCAL BACKUP MODE ---
                // Save database state for this specific account locally
                val books = dao.getAllBooks(email)
                val pages = mutableListOf<PageEntity>()
                for (book in books) {
                    pages.addAll(dao.getPagesForBook(book.id))
                }
                val notes = mutableListOf<NoteEntity>()
                for (page in pages) {
                    notes.addAll(dao.getNotesForPage(page.id))
                }
                val chatSessions = dao.getAllChatSessions(email)

                val payload = DriveBackupPayload(
                    books = books,
                    pages = pages,
                    notes = notes,
                    chatSessions = chatSessions,
                    apiKey = null,
                    timestamp = System.currentTimeMillis()
                )

                val jsonAdapter = moshi.adapter(DriveBackupPayload::class.java)
                val jsonString = jsonAdapter.toJson(payload)

                // Write backup to persistent storage for this account
                localBackupFile.writeText(jsonString)

                // Mark notes as synced locally
                for (note in notes) {
                    if (!note.isSynced) {
                        dao.insertNote(note.copy(isSynced = true))
                    }
                }

                val successMsg = if (email == "offline") {
                    "Respaldo local de Modo Offline completado: ${books.size} Libros, ${notes.size} Notas guardadas de forma segura en tu dispositivo."
                } else {
                    "Respaldo local completado para $email. Los cambios se sincronizarán en la nube al volver a estar en línea."
                }
                _syncState.value = SyncState.Success(successMsg, System.currentTimeMillis())
                
                // If we synced successfully using local fallback for a real account, mark as connected
                if (email != "offline") {
                    prefs.edit().putString("google_email", email).apply()
                    _isConnected.value = true
                }
            }

        } catch (e: Exception) {
            Log.e("DriveSyncManager", "Sincronización falló", e)
            _syncState.value = SyncState.Error("Error al sincronizar: ${e.localizedMessage}")
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Error Sync: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Merge remote backup data into the local database using updated timestamps as a source of truth.
     */
    private suspend fun mergeBackup(remote: DriveBackupPayload, targetEmail: String) {
        val dao = database.notesDao()

        // 1. Merge Books
        val localBooks = dao.getAllBooks(targetEmail)
        val localBooksMap = localBooks.associateBy { it.id }
        for (remoteBook in remote.books) {
            val bookToInsert = remoteBook.copy(userEmail = targetEmail)
            val localBook = localBooksMap[remoteBook.id]
            if (localBook == null || remoteBook.updatedAt > localBook.updatedAt) {
                dao.insertBook(bookToInsert)
            }
        }

        // 2. Merge Pages
        for (remotePage in remote.pages) {
            val pageToInsert = remotePage.copy(userEmail = targetEmail)
            val localPage = dao.getPageById(remotePage.id)
            if (localPage == null || remotePage.updatedAt > localPage.updatedAt) {
                dao.insertPage(pageToInsert)
            }
        }

        // 3. Merge Notes
        for (remoteNote in remote.notes) {
            val noteToInsert = remoteNote.copy(userEmail = targetEmail, isSynced = true)
            val localNote = dao.getNoteById(remoteNote.id)
            if (localNote == null || remoteNote.updatedAt > localNote.updatedAt) {
                dao.insertNote(noteToInsert)
            }
        }

        // 4. Merge Chat Sessions
        val remoteChats = remote.chatSessions ?: emptyList()
        for (remoteChat in remoteChats) {
            val chatToInsert = remoteChat.copy(userEmail = targetEmail)
            val localChat = dao.getChatSessionById(remoteChat.id)
            if (localChat == null) {
                dao.insertChatSession(chatToInsert)
            }
        }
    }

    private fun getFileInfo(context: Context, uri: Uri): Pair<String, String> {
        var name = "attachment_${UUID.randomUUID()}"
        var mime = "application/octet-stream"
        try {
            mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIdx)
                }
            }
        } catch (e: Exception) {
            Log.e("DriveSyncManager", "Error query URI info", e)
        }
        return Pair(name, mime)
    }

    private suspend fun uploadFileToDrive(context: Context, uriString: String, token: String): String? = withContext(Dispatchers.IO) {
        try {
            val fileObj = if (!uriString.startsWith("content://") && !uriString.startsWith("file://")) {
                val f = File(uriString)
                if (f.exists()) f else null
            } else if (uriString.startsWith("file://")) {
                val path = Uri.parse(uriString).path
                val f = if (path != null) File(path) else null
                if (f != null && f.exists()) f else null
            } else {
                null
            }

            val name: String
            val mime: String
            val bytes: ByteArray

            if (fileObj != null) {
                name = fileObj.name
                val ext = fileObj.extension.lowercase()
                mime = when (ext) {
                    "pdf" -> "application/pdf"
                    "png" -> "image/png"
                    "jpg", "jpeg" -> "image/jpeg"
                    "webp" -> "image/webp"
                    "gif" -> "image/gif"
                    "txt" -> "text/plain"
                    "md" -> "text/markdown"
                    "mp3" -> "audio/mpeg"
                    "wav" -> "audio/wav"
                    "m4a" -> "audio/mp4"
                    "mp4" -> "video/mp4"
                    else -> "application/octet-stream"
                }
                bytes = fileObj.readBytes()
            } else {
                val uri = Uri.parse(uriString)
                val (n, m) = getFileInfo(context, uri)
                name = n
                mime = m
                bytes = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.readBytes()
                } ?: return@withContext null
            }
            
            // 1. Create file metadata
            val metaUrl = "https://www.googleapis.com/drive/v3/files"
            val metaJson = JSONObject()
                .put("name", name)
                .put("mimeType", mime)
                .put("parents", org.json.JSONArray().put("appDataFolder"))
            val metaType = "application/json".toMediaTypeOrNull()
            val metaBody = metaJson.toString().toRequestBody(metaType)

            val metaRequest = Request.Builder()
                .url(metaUrl)
                .post(metaBody)
                .header("Authorization", "Bearer $token")
                .build()

            var createdFileId: String? = null
            client.newCall(metaRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val resBody = response.body?.string() ?: ""
                    createdFileId = JSONObject(resBody).optString("id")
                } else {
                    Log.e("DriveSyncManager", "Failed to create metadata for $name: ${response.code}")
                }
            }

            if (createdFileId == null) return@withContext null

            // 2. Upload file content bytes
            val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$createdFileId?uploadType=media"
            
            val requestBody = bytes.toRequestBody(mime.toMediaTypeOrNull())
            val uploadRequest = Request.Builder()
                .url(uploadUrl)
                .patch(requestBody)
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(uploadRequest).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d("DriveSyncManager", "Uploaded attachment $name with ID $createdFileId successfully")
                    return@withContext "gdrive://$createdFileId"
                } else {
                    Log.e("DriveSyncManager", "Failed to upload attachment content: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("DriveSyncManager", "Error uploading attachment: $uriString", e)
        }
        return@withContext null
    }

    suspend fun uploadAttachmentsForNote(note: NoteEntity): NoteEntity = withContext(Dispatchers.IO) {
        if (!_isConnected.value) return@withContext note
        val email = _userEmail.value
        if (email.isNullOrEmpty() || email == "offline") return@withContext note

        // We need the token
        var token: String? = null
        try {
            val account = Account(email, "com.google")
            val scope = "oauth2:https://www.googleapis.com/auth/drive.appdata"
            token = GoogleAuthUtil.getToken(context, account, scope)
        } catch (recoverable: com.google.android.gms.auth.UserRecoverableAuthException) {
            Log.w("DriveSyncManager", "Se requiere autorización del usuario para subir adjuntos", recoverable)
            _recoveryIntent.value = recoverable.intent
        } catch (authEx: Exception) {
            Log.e("DriveSyncManager", "Failed to get token for uploading attachments", authEx)
        }

        if (token == null) return@withContext note

        try {
            val content = note.content
            if (!content.trim().startsWith("[")) return@withContext note // Not rich blocks

            val array = org.json.JSONArray(content)
            var modified = false
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val type = obj.optString("type")
                when (type) {
                    "image" -> {
                        val url = obj.optString("urlOrPath")
                        if (isLocalUri(url)) {
                            Log.d("DriveSyncManager", "Uploading local image: $url")
                            val driveUrl = uploadFileToDrive(context, url, token)
                            if (driveUrl != null) {
                                obj.put("urlOrPath", driveUrl)
                                modified = true
                            }
                        }
                    }
                    "audio" -> {
                        val url = obj.optString("sourceUrl")
                        if (isLocalUri(url)) {
                            Log.d("DriveSyncManager", "Uploading local audio: $url")
                            val driveUrl = uploadFileToDrive(context, url, token)
                            if (driveUrl != null) {
                                obj.put("sourceUrl", driveUrl)
                                modified = true
                            }
                        }
                    }
                    "video" -> {
                        val url = obj.optString("sourceUrl")
                        if (isLocalUri(url)) {
                            Log.d("DriveSyncManager", "Uploading local video: $url")
                            val driveUrl = uploadFileToDrive(context, url, token)
                            if (driveUrl != null) {
                                obj.put("sourceUrl", driveUrl)
                                modified = true
                            }
                        }
                    }
                    "file" -> {
                        val url = obj.optString("sourceUrl")
                        if (isLocalUri(url)) {
                            Log.d("DriveSyncManager", "Uploading local file: $url")
                            val driveUrl = uploadFileToDrive(context, url, token)
                            if (driveUrl != null) {
                                obj.put("sourceUrl", driveUrl)
                                modified = true
                            }
                        }
                    }
                }
            }

            if (modified) {
                val updatedContent = array.toString()
                return@withContext note.copy(content = updatedContent, isSynced = false, updatedAt = System.currentTimeMillis())
            }
        } catch (e: Exception) {
            Log.e("DriveSyncManager", "Error processing attachments for note ${note.id}", e)
        }

        return@withContext note
    }

    private fun isLocalUri(url: String): Boolean {
        return url.isNotEmpty() && (url.startsWith("content://") || url.startsWith("file://") || (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("gdrive://") && url != "Mantener pulsado para editar"))
    }

    suspend fun getLocalFileForDriveUri(gdriveUri: String): File? = withContext(Dispatchers.IO) {
        if (!gdriveUri.startsWith("gdrive://")) return@withContext null
        val fileId = gdriveUri.removePrefix("gdrive://")
        val cacheFile = File(context.cacheDir, "gdrive_$fileId")
        if (cacheFile.exists()) {
            return@withContext cacheFile
        }

        // We need the token
        val email = _userEmail.value
        if (email.isNullOrEmpty() || email == "offline") return@withContext null

        var token: String? = null
        try {
            val account = Account(email, "com.google")
            val scope = "oauth2:https://www.googleapis.com/auth/drive.appdata"
            token = GoogleAuthUtil.getToken(context, account, scope)
        } catch (recoverable: com.google.android.gms.auth.UserRecoverableAuthException) {
            Log.w("DriveSyncManager", "Se requiere autorización del usuario para descargar adjuntos", recoverable)
            _recoveryIntent.value = recoverable.intent
        } catch (authEx: Exception) {
            Log.e("DriveSyncManager", "Failed to get token for downloading", authEx)
        }

        if (token == null) return@withContext null

        try {
            val downloadUrl = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
            val downloadRequest = Request.Builder()
                .url(downloadUrl)
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(downloadRequest).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.byteStream()?.use { input ->
                        cacheFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d("DriveSyncManager", "Downloaded file $fileId to local cache successfully")
                    return@withContext cacheFile
                } else {
                    Log.e("DriveSyncManager", "Download failed for file $fileId: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("DriveSyncManager", "Error downloading $fileId", e)
        }
        return@withContext null
    }

    suspend fun createDriveDatabaseStructure(): Result<String> = withContext(Dispatchers.IO) {
        val email = _userEmail.value
        if (email.isNullOrEmpty() || email == "offline") {
            val msg = "Debes iniciar sesión con una cuenta de Google para crear la estructura en Google Drive."
            _syncState.value = SyncState.Error(msg)
            return@withContext Result.failure(Exception(msg))
        }

        _syncState.value = SyncState.Syncing
        val account = Account(email, "com.google")
        val primaryScope = "oauth2:https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive.appdata"
        val fallbackScope = "oauth2:https://www.googleapis.com/auth/drive.appdata"
        var token: String? = null
        try {
            token = GoogleAuthUtil.getToken(context, account, primaryScope)
        } catch (recoverable: com.google.android.gms.auth.UserRecoverableAuthException) {
            _recoveryIntent.value = recoverable.intent
            _syncState.value = SyncState.Error("Se requiere autorización para acceder a Google Drive.")
            return@withContext Result.failure(recoverable)
        } catch (authEx: Exception) {
            try {
                token = GoogleAuthUtil.getToken(context, account, fallbackScope)
            } catch (recoverable2: com.google.android.gms.auth.UserRecoverableAuthException) {
                _recoveryIntent.value = recoverable2.intent
                _syncState.value = SyncState.Error("Se requiere autorización para Google Drive.")
                return@withContext Result.failure(recoverable2)
            } catch (authEx2: Exception) {
                _syncState.value = SyncState.Error("Error de autenticación Google: ${authEx2.localizedMessage}")
                return@withContext Result.failure(authEx2)
            }
        }

        if (token == null) {
            _syncState.value = SyncState.Error("No se pudo obtener el token de acceso a Google Drive.")
            return@withContext Result.failure(Exception("Token nulo"))
        }

        val dao = database.notesDao()
        val books = dao.getAllBooks(email)
        val pages = mutableListOf<PageEntity>()
        for (book in books) {
            pages.addAll(dao.getPagesForBook(book.id))
        }
        val notes = dao.getAllNotes(email)
        val chatSessions = dao.getAllChatSessions(email)

        val rootFolderId = syncDatabaseStructureToDrive(token, email, books, pages, notes, chatSessions)
        if (rootFolderId != null) {
            val msg = "¡Estructura de Base de Datos creada en Google Drive! Carpeta 'UTK Notes - Database' organizada con ${books.size} Libros, ${pages.size} Páginas, ${notes.size} Notas, Banco de Memoria y Chats."
            _syncState.value = SyncState.Success(msg, System.currentTimeMillis())
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            }
            return@withContext Result.success(rootFolderId)
        } else {
            val msg = "No se pudo crear la estructura en Google Drive. Verifica tu conexión o permisos."
            _syncState.value = SyncState.Error(msg)
            return@withContext Result.failure(Exception(msg))
        }
    }

    private fun sanitizeTitle(text: String): String {
        return text.replace("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ _-]".toRegex(), "")
            .trim()
            .ifEmpty { "unnamed" }
            .take(40)
    }

    private fun getOrCreateFolder(token: String, name: String, parentId: String?): String? {
        val safeName = sanitizeTitle(name)
        val query = if (parentId != null) {
            "name = '$safeName' and '$parentId' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        } else {
            "name = '$safeName' and 'root' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        }
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id,name)"
        val searchReq = Request.Builder()
            .url(searchUrl)
            .header("Authorization", "Bearer $token")
            .build()

        try {
            client.newCall(searchReq).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val files = json.optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        return files.getJSONObject(0).optString("id")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("DriveSyncManager", "Error buscando carpeta: $safeName", e)
        }

        // Folder doesn't exist, create it
        val createUrl = "https://www.googleapis.com/drive/v3/files"
        val meta = JSONObject()
            .put("name", safeName)
            .put("mimeType", "application/vnd.google-apps.folder")
        if (parentId != null) {
            meta.put("parents", org.json.JSONArray().put(parentId))
        }
        val body = meta.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val createReq = Request.Builder()
            .url(createUrl)
            .post(body)
            .header("Authorization", "Bearer $token")
            .build()

        try {
            client.newCall(createReq).execute().use { response ->
                if (response.isSuccessful) {
                    val res = JSONObject(response.body?.string() ?: "")
                    return res.optString("id")
                } else {
                    Log.e("DriveSyncManager", "Error creando carpeta $safeName: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("DriveSyncManager", "Excepción creando carpeta $safeName", e)
        }
        return null
    }

    private fun uploadOrUpdateJsonFile(token: String, name: String, parentId: String, content: String): String? {
        val safeName = name.replace("'", "").trim().take(60)
        val query = "name = '$safeName' and '$parentId' in parents and trashed = false"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id,name)"
        val searchReq = Request.Builder()
            .url(searchUrl)
            .header("Authorization", "Bearer $token")
            .build()

        var existingFileId: String? = null
        try {
            client.newCall(searchReq).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val files = json.optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        existingFileId = files.getJSONObject(0).optString("id")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("DriveSyncManager", "Error buscando archivo: $safeName", e)
        }

        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody = content.toRequestBody(mediaType)

        if (existingFileId != null) {
            val updateUrl = "https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=media"
            val patchReq = Request.Builder()
                .url(updateUrl)
                .patch(requestBody)
                .header("Authorization", "Bearer $token")
                .build()
            try {
                client.newCall(patchReq).execute().use { response ->
                    if (response.isSuccessful) {
                        return existingFileId
                    }
                }
            } catch (e: Exception) {
                Log.e("DriveSyncManager", "Error actualizando contenido de $safeName", e)
            }
        } else {
            val createUrl = "https://www.googleapis.com/drive/v3/files"
            val meta = JSONObject()
                .put("name", safeName)
                .put("mimeType", "application/json")
                .put("parents", org.json.JSONArray().put(parentId))
            val metaBody = meta.toString().toRequestBody(mediaType)
            val createReq = Request.Builder()
                .url(createUrl)
                .post(metaBody)
                .header("Authorization", "Bearer $token")
                .build()

            var createdId: String? = null
            try {
                client.newCall(createReq).execute().use { response ->
                    if (response.isSuccessful) {
                        val res = JSONObject(response.body?.string() ?: "")
                        createdId = res.optString("id")
                    }
                }
            } catch (e: Exception) {
                Log.e("DriveSyncManager", "Error creando metadata para $safeName", e)
            }

            if (createdId != null) {
                val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$createdId?uploadType=media"
                val patchReq = Request.Builder()
                    .url(uploadUrl)
                    .patch(requestBody)
                    .header("Authorization", "Bearer $token")
                    .build()
                try {
                    client.newCall(patchReq).execute().use { response ->
                        if (response.isSuccessful) {
                            return createdId
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DriveSyncManager", "Error subiendo bytes de $safeName", e)
                }
            }
        }
        return null
    }

    private suspend fun syncDatabaseStructureToDrive(
        token: String,
        email: String,
        books: List<BookEntity>,
        pages: List<PageEntity>,
        notes: List<NoteEntity>,
        chatSessions: List<ChatSessionEntity>
    ): String? = withContext(Dispatchers.IO) {
        try {
            Log.d("DriveSyncManager", "Creando/sincronizando jerarquía de base de datos en Google Drive...")
            // 1. Root Folder
            var rootFolderId = getOrCreateFolder(token, "UTK Notes - Database", null)
            if (rootFolderId == null) {
                Log.w("DriveSyncManager", "No se pudo crear en raíz de Drive, intentando con fallback...")
                rootFolderId = getOrCreateFolder(token, "UTK Notes - Database", "appDataFolder")
            }
            if (rootFolderId == null) {
                Log.e("DriveSyncManager", "No se pudo obtener carpeta raíz en Drive")
                return@withContext null
            }

            // 2. Schema folder & schema_version.json
            val schemaFolderId = getOrCreateFolder(token, "schema", rootFolderId)
            if (schemaFolderId != null) {
                val schemaJson = JSONObject()
                    .put("database_name", "UTK_Notes_DB")
                    .put("version", 1)
                    .put("account", email)
                    .put("updated_at", System.currentTimeMillis())
                    .put("description", "Base de datos jerárquica con soporte para IA local Qwen2.5 y banco de memoria por nota")
                    .put("tables", JSONObject()
                        .put("books", JSONObject()
                            .put("primary_key", "id")
                            .put("columns", org.json.JSONArray(listOf("id", "title", "colorHex", "textColorHex", "coverUri", "userEmail", "createdAt", "updatedAt"))))
                        .put("pages", JSONObject()
                            .put("primary_key", "id")
                            .put("foreign_key", "bookId -> books.id")
                            .put("columns", org.json.JSONArray(listOf("id", "bookId", "title", "orderIndex", "userEmail", "createdAt", "updatedAt"))))
                        .put("notes", JSONObject()
                            .put("primary_key", "id")
                            .put("foreign_key", "pageId -> pages.id")
                            .put("columns", org.json.JSONArray(listOf("id", "pageId", "title", "content", "tags", "attachments", "reminderTime", "reminderStatus", "isPinned", "isSynced", "userEmail", "createdAt", "updatedAt"))))
                        .put("chat_sessions", JSONObject()
                            .put("primary_key", "id")
                            .put("columns", org.json.JSONArray(listOf("id", "userEmail", "title", "messagesJson", "createdAt"))))
                        .put("memory_bank", JSONObject()
                            .put("foreign_key", "noteId -> notes.id")
                            .put("columns", org.json.JSONArray(listOf("noteId", "title", "summary", "keyFacts", "topics", "customContext", "lastUpdated"))))
                    )
                    .put("ai_engine", JSONObject()
                        .put("local_model", "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf")
                        .put("runtime", "llama.cpp ARM NEON")
                        .put("memory_vault", "Hierarchical File-Based Memory Bank")
                    )
                uploadOrUpdateJsonFile(token, "schema_version.json", schemaFolderId, schemaJson.toString(2))
            }

            // 3. Database Snapshot folder
            val backupFolderId = getOrCreateFolder(token, "database_backup", rootFolderId)
            if (backupFolderId != null) {
                val payload = DriveBackupPayload(
                    books = books,
                    pages = pages,
                    notes = notes,
                    chatSessions = chatSessions,
                    apiKey = null,
                    timestamp = System.currentTimeMillis()
                )
                val jsonAdapter = moshi.adapter(DriveBackupPayload::class.java)
                val snapshotString = jsonAdapter.toJson(payload)
                uploadOrUpdateJsonFile(token, "database_snapshot.json", backupFolderId, snapshotString)
            }

            // 4. Books hierarchy
            val booksFolderId = getOrCreateFolder(token, "books", rootFolderId)
            if (booksFolderId != null) {
                val pagesByBook = pages.groupBy { it.bookId }
                val notesByPage = notes.groupBy { it.pageId }

                for (book in books) {
                    val cleanBookTitle = sanitizeTitle(book.title)
                    val bookFolderName = "book_${cleanBookTitle}_${book.id.take(8)}"
                    val bookFolderId = getOrCreateFolder(token, bookFolderName, booksFolderId) ?: continue

                    // Book Meta
                    val bookMetaJson = JSONObject()
                        .put("id", book.id)
                        .put("title", book.title)
                        .put("colorHex", book.colorHex)
                        .put("textColorHex", book.textColorHex)
                        .put("coverUri", book.coverUri ?: "")
                        .put("userEmail", book.userEmail)
                        .put("createdAt", book.createdAt)
                        .put("updatedAt", book.updatedAt)
                    uploadOrUpdateJsonFile(token, "book_meta.json", bookFolderId, bookMetaJson.toString(2))

                    // Pages Subfolder
                    val pagesFolderId = getOrCreateFolder(token, "pages", bookFolderId) ?: continue
                    val bookPages = pagesByBook[book.id] ?: emptyList()

                    for (page in bookPages) {
                        val cleanPageTitle = sanitizeTitle(page.title)
                        val pageFolderName = "page_${cleanPageTitle}_${page.id.take(8)}"
                        val pageFolderId = getOrCreateFolder(token, pageFolderName, pagesFolderId) ?: continue

                        // Page Meta
                        val pageMetaJson = JSONObject()
                            .put("id", page.id)
                            .put("bookId", page.bookId)
                            .put("title", page.title)
                            .put("userEmail", page.userEmail)
                            .put("createdAt", page.createdAt)
                            .put("updatedAt", page.updatedAt)
                        uploadOrUpdateJsonFile(token, "page_meta.json", pageFolderId, pageMetaJson.toString(2))

                        // Notes Subfolder
                        val notesFolderId = getOrCreateFolder(token, "notes", pageFolderId) ?: continue
                        val pageNotes = notesByPage[page.id] ?: emptyList()

                        for (note in pageNotes) {
                            val cleanNoteTitle = sanitizeTitle(note.title)
                            val noteFolderName = "note_${cleanNoteTitle}_${note.id.take(8)}"
                            val noteFolderId = getOrCreateFolder(token, noteFolderName, notesFolderId) ?: continue

                            // 1. note.json
                            val noteJson = JSONObject()
                                .put("id", note.id)
                                .put("pageId", note.pageId)
                                .put("title", note.title)
                                .put("content", note.content)
                                .put("tags", note.tags)
                                .put("attachments", note.attachments)
                                .put("reminderTime", note.reminderTime ?: 0L)
                                .put("reminderStatus", note.reminderStatus)
                                .put("isPinned", note.isPinned)
                                .put("isSynced", note.isSynced)
                                .put("userEmail", note.userEmail)
                                .put("createdAt", note.createdAt)
                                .put("updatedAt", note.updatedAt)
                            uploadOrUpdateJsonFile(token, "note.json", noteFolderId, noteJson.toString(2))

                            // 2. memory_bank.json
                            val memoryBank = memoryVault.getMemoryBank(note.id)
                            val bankJson = JSONObject()
                                .put("noteId", note.id)
                                .put("title", note.title)
                                .put("summary", memoryBank.summary)
                                .put("keyFacts", org.json.JSONArray(memoryBank.keyFacts))
                                .put("topics", org.json.JSONArray(memoryBank.topics))
                                .put("customContext", memoryBank.customContext)
                                .put("lastUpdated", memoryBank.lastUpdated)
                            uploadOrUpdateJsonFile(token, "memory_bank.json", noteFolderId, bankJson.toString(2))

                            // 3. chats subfolder
                            val chatsFolderId = getOrCreateFolder(token, "chats", noteFolderId)
                            if (chatsFolderId != null) {
                                val chats = memoryVault.listChatsForNote(note.id)
                                for (chat in chats) {
                                    val chatJson = JSONObject()
                                        .put("chatId", chat.chatId)
                                        .put("noteId", chat.noteId)
                                        .put("title", chat.title)
                                        .put("createdAt", chat.createdAt)
                                        .put("updatedAt", chat.updatedAt)
                                        .put("messages", org.json.JSONArray().apply {
                                            chat.messages.forEach { m ->
                                                put(JSONObject().put("role", m.role).put("content", m.content).put("timestamp", m.timestamp))
                                            }
                                        })
                                    val safeChatId = chat.chatId.take(12)
                                    uploadOrUpdateJsonFile(token, "chat_$safeChatId.json", chatsFolderId, chatJson.toString(2))
                                }
                            }
                        }
                    }
                }
            }

            // 5. AI Vault folder
            val aiVaultFolderId = getOrCreateFolder(token, "ai_vault", rootFolderId)
            if (aiVaultFolderId != null) {
                val aiConfigJson = JSONObject()
                    .put("model_name", "Qwen2.5-1.5B-Instruct-Q4_K_M")
                    .put("model_file", "qwen2.5-1.5b-instruct-q4_k_m.gguf")
                    .put("device_storage", "internal_files_dir/models")
                    .put("context_size", 2048)
                    .put("threads", 4)
                    .put("temperature", 0.7)
                    .put("memory_injection_strategy", "ExecutiveSummary + AtomicKeyFacts (300-500 tokens)")
                    .put("last_sync", System.currentTimeMillis())
                uploadOrUpdateJsonFile(token, "qwen_ai_config.json", aiVaultFolderId, aiConfigJson.toString(2))
            }

            // 6. Attachments folder
            getOrCreateFolder(token, "attachments", rootFolderId)

            Log.d("DriveSyncManager", "Jerarquía de base de datos en Drive completada con éxito. Root ID: $rootFolderId")
            rootFolderId
        } catch (e: Exception) {
            Log.e("DriveSyncManager", "Error creando estructura de base de datos en Drive", e)
            null
        }
    }
}
