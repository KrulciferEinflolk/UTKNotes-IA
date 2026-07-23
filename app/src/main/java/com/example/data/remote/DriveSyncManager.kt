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
import com.google.android.gms.auth.GoogleAuthUtil
import android.accounts.Account
import android.accounts.AccountManager

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
    data class Error(val message: String) : SyncState()
}

class DriveSyncManager(
    private val context: Context,
    private val database: AppDatabase
) {
    private val prefs = context.getSharedPreferences("utk_notes_prefs", Context.MODE_PRIVATE)

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

    // Store custom API Key per account
    var geminiApiKey: String?
        get() {
            val email = _userEmail.value ?: "offline"
            return prefs.getString("gemini_api_key_$email", prefs.getString("gemini_api_key", null))
        }
        set(value) {
            val email = _userEmail.value ?: "offline"
            prefs.edit().putString("gemini_api_key_$email", value).apply()
        }

    init {
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
        prefs.edit().putBoolean("auto_login_disabled", false).apply()
        if (email == "offline") {
            prefs.edit().putString("google_email", "offline").apply()
            _userEmail.value = "offline"
            _isConnected.value = true
            _syncState.value = SyncState.Success("Iniciado en Modo Local (offline)", System.currentTimeMillis())
        } else {
            _userEmail.value = email
            _syncState.value = SyncState.Success("Conectando con $email...", System.currentTimeMillis())
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
                val scope = "oauth2:https://www.googleapis.com/auth/drive.file"
                try {
                    token = GoogleAuthUtil.getToken(context, account, scope)
                    Log.d("DriveSyncManager", "OAuth Token obtenido correctamente")
                } catch (recoverable: com.google.android.gms.auth.UserRecoverableAuthException) {
                    Log.w("DriveSyncManager", "Se requiere autorización del usuario para acceder a Drive", recoverable)
                    _recoveryIntent.value = recoverable.intent
                    _syncState.value = SyncState.Error("Se requiere autorización para acceder a Google Drive.")
                } catch (authEx: Exception) {
                    Log.e("DriveSyncManager", "No se pudo obtener el token de Google. Se usará respaldo local.", authEx)
                }
            }

            if (token != null && email != "offline") {
                // --- ONLINE GOOGLE DRIVE SYNC ---
                val searchUrl = "https://www.googleapis.com/drive/v3/files?q=name='utk_notes_ia_backup.json' and trashed=false&spaces=drive"
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
                        if (files != null && files.length() > 0) {
                            fileId = files.getJSONObject(0).optString("id")
                            Log.d("DriveSyncManager", "Archivo de respaldo encontrado en Drive: $fileId")
                        }
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
                val notes = mutableListOf<NoteEntity>()
                for (page in pages) {
                    val pageNotes = dao.getNotesForPage(page.id)
                    for (n in pageNotes) {
                        val updatedNote = uploadAttachmentsForNote(n)
                        if (updatedNote.content != n.content) {
                            dao.insertNote(updatedNote)
                            notes.add(updatedNote)
                        } else {
                            notes.add(n)
                        }
                    }
                }
                val chatSessions = dao.getAllChatSessions(email)

                val payload = DriveBackupPayload(
                    books = books,
                    pages = pages,
                    notes = notes,
                    chatSessions = chatSessions,
                    apiKey = geminiApiKey,
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
                            throw Exception("Error de red al actualizar Drive: ${response.code}")
                        }
                    }
                } else {
                    // Create new file on Google Drive
                    val metaUrl = "https://www.googleapis.com/drive/v3/files"
                    val metaJson = JSONObject()
                        .put("name", "utk_notes_ia_backup.json")
                        .put("mimeType", "application/json")
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
                            throw Exception("Error de red creando archivo en Drive: ${response.code}")
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
                                throw Exception("Error de red subiendo contenido a Drive: ${response.code}")
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

                val successMsg = "¡Sincronizado con éxito! Se respaldaron ${books.size} Libros, ${pages.size} Páginas, ${notes.size} Notas e historial de IA en Google Drive."
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
                    apiKey = geminiApiKey,
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

        // 5. Merge Gemini API Key
        if (!remote.apiKey.isNullOrEmpty()) {
            geminiApiKey = remote.apiKey
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
            val scope = "oauth2:https://www.googleapis.com/auth/drive.file"
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
            val scope = "oauth2:https://www.googleapis.com/auth/drive.file"
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
}
