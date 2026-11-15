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
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.util.Base64
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.FormBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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

sealed class AuthResolutionOutcome {
    object Authorized : AuthResolutionOutcome()
    data class Failed(val diagnosticMessage: String) : AuthResolutionOutcome()
}

class DriveSyncManager(
    private val context: Context,
    private val database: AppDatabase
) {
    companion object {
        const val DEFAULT_WEB_OAUTH_CLIENT_ID =
            "854876463054-06pc7gofcrao1f15d85b6jii2o61ts9f.apps.googleusercontent.com"
        private const val PREF_CUSTOM_OAUTH_CLIENT_ID = "custom_oauth_client_id"
        private const val PREF_CUSTOM_OAUTH_CLIENT_SECRET = "custom_oauth_client_secret"

        fun generatePkceCodeVerifier(): String {
            val bytes = ByteArray(48)
            SecureRandom().nextBytes(bytes)
            return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        }

        fun computePkceCodeChallenge(codeVerifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(codeVerifier.toByteArray(Charsets.US_ASCII))
            return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        }

        fun buildBrowserOAuthUrl(
            clientId: String,
            redirectUri: String,
            codeChallenge: String,
            state: String,
            loginHint: String?
        ): String {
            val scopes = "https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive.appdata"
            val builder = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth").buildUpon()
                .appendQueryParameter("client_id", clientId.trim())
                .appendQueryParameter("redirect_uri", redirectUri)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("scope", scopes)
                .appendQueryParameter("code_challenge", codeChallenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("state", state)
                .appendQueryParameter("access_type", "offline")
                .appendQueryParameter("prompt", "consent")
            if (!loginHint.isNullOrBlank() && loginHint != "offline") {
                builder.appendQueryParameter("login_hint", loginHint)
            }
            return builder.build().toString()
        }
    }

    private val prefs = context.getSharedPreferences("utk_notes_prefs", Context.MODE_PRIVATE)
    private val memoryVault = NoteMemoryVaultManager(context)

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail

    private val _recoveryIntent = MutableStateFlow<Intent?>(null)
    val recoveryIntent: StateFlow<Intent?> = _recoveryIntent

    private val _pendingAuthIntentSender = MutableStateFlow<IntentSender?>(null)
    val pendingAuthIntentSender: StateFlow<IntentSender?> = _pendingAuthIntentSender

    private val _gmsAuthDiagnostic = MutableStateFlow<String?>(null)
    val gmsAuthDiagnostic: StateFlow<String?> = _gmsAuthDiagnostic

    private val _browserAuthIntent = MutableStateFlow<Intent?>(null)
    val browserAuthIntent: StateFlow<Intent?> = _browserAuthIntent

    private val gmsBlockedAccounts = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    @Volatile
    private var activeOAuthSocket: ServerSocket? = null

    @Volatile
    private var cachedAccessToken: String? = null
    @Volatile
    private var cachedTokenEmail: String? = null
    @Volatile
    private var cachedTokenExpiresAtMs: Long = 0L

    fun clearRecoveryIntent() {
        _recoveryIntent.value = null
    }

    fun clearPendingAuthIntentSender() {
        _pendingAuthIntentSender.value = null
    }

    fun clearBrowserAuthIntent() {
        _browserAuthIntent.value = null
    }

    fun clearGmsAuthDiagnostic() {
        _gmsAuthDiagnostic.value = null
    }

    fun normalizeAccountKey(accountId: String): String {
        val clean = accountId.trim()
        return if (clean.isEmpty() || clean.equals("offline", ignoreCase = true)) {
            "offline"
        } else {
            clean.lowercase()
        }
    }

    fun isGmsBlockedForAccount(email: String): Boolean {
        return gmsBlockedAccounts.contains(normalizeAccountKey(email))
    }

    fun resetGmsBlock(email: String? = null) {
        if (email == null) {
            gmsBlockedAccounts.clear()
        } else {
            gmsBlockedAccounts.remove(normalizeAccountKey(email))
        }
        _gmsAuthDiagnostic.value = null
    }

    fun getApkSigningSha1(): String = getAppSha1Fingerprint()

    fun getAppPackageName(): String = context.packageName

    fun getDefaultWebClientId(): String = DEFAULT_WEB_OAUTH_CLIENT_ID

    fun getSavedCustomOAuthClientId(): String {
        return prefs.getString(PREF_CUSTOM_OAUTH_CLIENT_ID, null)?.trim()?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_WEB_OAUTH_CLIENT_ID
    }

    fun getSavedCustomOAuthClientSecret(): String {
        return prefs.getString(PREF_CUSTOM_OAUTH_CLIENT_SECRET, null).orEmpty()
    }

    fun saveCustomOAuthConfig(clientId: String, clientSecret: String) {
        prefs.edit()
            .putString(PREF_CUSTOM_OAUTH_CLIENT_ID, clientId.trim())
            .putString(PREF_CUSTOM_OAUTH_CLIENT_SECRET, clientSecret.trim())
            .commit()
    }

    fun hasBrowserOAuthCredentials(email: String): Boolean {
        val key = normalizeAccountKey(email)
        if (key == "offline") return false
        val token = prefs.getString("custom_oauth_access_token_$key", null)
        val refresh = prefs.getString("custom_oauth_refresh_token_$key", null)
        return !token.isNullOrBlank() || !refresh.isNullOrBlank()
    }

    fun invalidateCachedAccessToken() {
        cachedAccessToken = null
        cachedTokenEmail = null
        cachedTokenExpiresAtMs = 0L
    }

    fun onAuthorizationResultFromIntent(data: Intent?): Boolean {
        _pendingAuthIntentSender.value = null
        val email = _userEmail.value ?: return false
        if (data == null) return false
        return try {
            val authResult = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(data)
            val token = authResult.accessToken
            if (!token.isNullOrBlank()) {
                cachedAccessToken = token
                cachedTokenEmail = email
                cachedTokenExpiresAtMs = System.currentTimeMillis() + 45L * 60_000L
                gmsBlockedAccounts.remove(normalizeAccountKey(email))
                _gmsAuthDiagnostic.value = null
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("DriveSyncManager", "Error parsing AuthorizationResult intent", e)
            false
        }
    }

    suspend fun handleAuthorizationResolutionResult(
        resultCode: Int,
        data: Intent?
    ): AuthResolutionOutcome = withContext(Dispatchers.IO) {
        _pendingAuthIntentSender.value = null
        val email = _userEmail.value
        if (email.isNullOrBlank() || email == "offline") {
            return@withContext AuthResolutionOutcome.Failed("No hay una cuenta de Google activa.")
        }

        var apiStatusCode: Int? = null
        var apiErrorDetails: String? = null
        if (data != null) {
            try {
                val authResult = Identity.getAuthorizationClient(context)
                    .getAuthorizationResultFromIntent(data)
                val token = authResult.accessToken
                if (!token.isNullOrBlank()) {
                    cachedAccessToken = token
                    cachedTokenEmail = email
                    cachedTokenExpiresAtMs = System.currentTimeMillis() + 45L * 60_000L
                    gmsBlockedAccounts.remove(normalizeAccountKey(email))
                    _gmsAuthDiagnostic.value = null
                    return@withContext AuthResolutionOutcome.Authorized
                }
            } catch (apiEx: ApiException) {
                apiStatusCode = apiEx.statusCode
                apiErrorDetails = apiEx.localizedMessage ?: "Código GMS ${apiEx.statusCode}"
                Log.w("DriveSyncManager", "GMS Auth ApiException (${apiEx.statusCode}): ${apiEx.message}")
            } catch (e: Exception) {
                apiErrorDetails = e.localizedMessage
                Log.w("DriveSyncManager", "GMS Auth Intent error: ${e.message}")
            }
        }

        gmsBlockedAccounts.add(normalizeAccountKey(email))
        runCatching { saveLocalBackupSnapshot(email) }
        val diagnostic = buildGmsFailureDiagnostic(
            email = email,
            resultCode = resultCode,
            statusCode = apiStatusCode,
            rawDetails = apiErrorDetails
        )
        _gmsAuthDiagnostic.value = diagnostic
        _syncState.value = SyncState.Error(diagnostic)
        AuthResolutionOutcome.Failed(diagnostic)
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
            registerKnownAccount(savedEmail)
            _userEmail.value = savedEmail
            _isConnected.value = true
        } else {
            _userEmail.value = null
            _isConnected.value = false
        }
    }

    fun getKnownAccounts(): Set<String> {
        val canonicalByKey = linkedMapOf<String, String>()
        canonicalByKey["offline"] = "offline"

        val storedSet = prefs.getStringSet("known_accounts", emptySet())?.toHashSet() ?: emptySet()
        for (raw in storedSet) {
            val clean = raw.trim()
            if (clean.isNotEmpty()) {
                val key = normalizeAccountKey(clean)
                canonicalByKey.putIfAbsent(key, if (key == "offline") "offline" else clean)
            }
        }

        // Discover any local backup JSON files from previously accessed accounts on this device
        runCatching {
            context.getExternalFilesDir(null)?.listFiles()?.forEach { file ->
                val name = file.name
                if (name.startsWith("utk_notes_backup_") && name.endsWith(".json")) {
                    val rawAcc = name.removePrefix("utk_notes_backup_").removeSuffix(".json").trim()
                    if (rawAcc.isNotEmpty()) {
                        val key = normalizeAccountKey(rawAcc)
                        canonicalByKey.putIfAbsent(key, if (key == "offline") "offline" else rawAcc)
                    }
                }
            }
        }

        _userEmail.value?.trim()?.takeIf { it.isNotEmpty() }?.let { active ->
            val key = normalizeAccountKey(active)
            canonicalByKey[key] = if (key == "offline") "offline" else active
        }
        return canonicalByKey.values.toCollection(linkedSetOf())
    }

    fun registerKnownAccount(accountId: String) {
        val clean = accountId.trim()
        if (clean.isEmpty()) return
        val normalizedNew = normalizeAccountKey(clean)
        val existingSet = prefs.getStringSet("known_accounts", emptySet())?.let { HashSet(it) } ?: HashSet()
        val updated = HashSet<String>()
        updated.add("offline")
        var alreadyContainsNormalized = normalizedNew == "offline"
        for (entry in existingSet) {
            val t = entry.trim()
            if (t.isNotEmpty()) {
                if (normalizeAccountKey(t) == normalizedNew) {
                    alreadyContainsNormalized = true
                    updated.add(t)
                } else {
                    updated.add(t)
                }
            }
        }
        if (!alreadyContainsNormalized) {
            updated.add(clean)
        }
        if (updated != existingSet || !existingSet.contains(clean) && !alreadyContainsNormalized) {
            prefs.edit().putStringSet("known_accounts", updated).commit()
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
        val cleanEmail = email.trim().ifBlank { "offline" }
        val normalizedNew = normalizeAccountKey(cleanEmail)
        if (cachedTokenEmail == null || normalizeAccountKey(cachedTokenEmail!!) != normalizedNew) {
            invalidateCachedAccessToken()
            _gmsAuthDiagnostic.value = null
        }
        registerKnownAccount(cleanEmail)
        prefs.edit()
            .putBoolean("auto_login_disabled", false)
            .putString("google_email", cleanEmail)
            .commit()
        _userEmail.value = cleanEmail
        _isConnected.value = true
        if (cleanEmail == "offline") {
            _gmsAuthDiagnostic.value = null
            _syncState.value = SyncState.Success("Iniciado en Modo Local (offline)", System.currentTimeMillis())
        } else {
            _syncState.value = SyncState.Success("Conectado con $cleanEmail", System.currentTimeMillis())
        }
    }

    fun disconnectDrive() {
        invalidateCachedAccessToken()
        clearRecoveryIntent()
        clearPendingAuthIntentSender()
        clearBrowserAuthIntent()
        _gmsAuthDiagnostic.value = null
        prefs.edit()
            .remove("google_email")
            .putBoolean("auto_login_disabled", true)
            .commit()
        _userEmail.value = null
        _isConnected.value = false
        _syncState.value = SyncState.Idle
    }

    private suspend fun saveLocalBackupSnapshot(email: String): Triple<Int, Int, Int> {
        val dao = database.notesDao()
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
        val jsonString = moshi.adapter(DriveBackupPayload::class.java).toJson(payload)
        val localBackupFile = File(context.getExternalFilesDir(null), "utk_notes_backup_${email}.json")
        localBackupFile.writeText(jsonString)
        return Triple(books.size, pages.size, notes.size)
    }

    /**
     * Performs active two-way synchronization between local Room DB and Google Drive.
     * Restores all books, pages, notes, multimedia metadata, and chat agent history.
     * Automatic/background callers should keep allowInteractiveAuth = false to avoid Play Services popups.
     */
    suspend fun synchronize(
        allowInteractiveAuth: Boolean = false,
        forceRetryGms: Boolean = false
    ): Unit = withContext(Dispatchers.IO) {
        val email = _userEmail.value
        if (email.isNullOrEmpty()) {
            _syncState.value = SyncState.Error("No se encontró correo asociado.")
            return@withContext
        }

        _syncState.value = SyncState.Syncing

        try {
            Log.d("DriveSyncManager", "Iniciando sincronización para: $email (interactive=$allowInteractiveAuth, retryGms=$forceRetryGms)")
            val dao = database.notesDao()

            // Preserve strict isolation per account: only normalize empty legacy userEmail rows to 'offline'
            try {
                dao.normalizeEmptyBooksToOffline()
                dao.normalizeEmptyPagesToOffline()
                dao.normalizeEmptyNotesToOffline()
                dao.normalizeEmptyChatSessionsToOffline()
            } catch (mEx: Exception) {
                Log.e("DriveSyncManager", "Error normalizando registros locales heredados", mEx)
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
                token = acquireDriveAccessToken(
                    email = email,
                    emitSyncErrors = allowInteractiveAuth || isGmsBlockedForAccount(email),
                    allowInteractiveAuth = allowInteractiveAuth,
                    forceRetryGms = forceRetryGms
                )
                if (token == null) {
                    // Always guarantee local JSON backup even when cloud OAuth cannot proceed
                    val (bookCount, _, noteCount) = saveLocalBackupSnapshot(email)
                    if (!allowInteractiveAuth && !isGmsBlockedForAccount(email)) {
                        _syncState.value = SyncState.Success(
                            "Guardado local ($bookCount libros, $noteCount notas). Pulsa 'Sincronizar ahora' o usa OAuth alternativo en Ajustes para subir a Drive.",
                            System.currentTimeMillis()
                        )
                    }
                    return@withContext
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
                        invalidateCachedAccessToken()
                        runCatching { GoogleAuthUtil.clearToken(context, token) }
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

                // Fetch final merged local data for this specific email to upload, respecting per-book & per-note Drive filters
                val driveFilter = com.example.data.local.settings.AppSettingsRepository(context).getDriveFilterSnapshot()
                val books = dao.getAllBooks(email).filter { driveFilter.isBookSynced(it.id) }
                val allowedBookIds = books.map { it.id }.toHashSet()
                val pages = mutableListOf<PageEntity>()
                for (book in books) {
                    pages.addAll(dao.getPagesForBook(book.id))
                }
                val pageToBookId = pages.associate { it.id to it.bookId }

                // Process only notes enabled for Drive sync so excluded items stay strictly local
                val rawNotes = dao.getAllNotes(email).filter { note ->
                    val parentBookId = pageToBookId[note.pageId]
                    val bookAllowed = parentBookId == null || parentBookId in allowedBookIds
                    bookAllowed && driveFilter.isNoteSynced(note.id, parentBookId)
                }
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
        val driveFilter = com.example.data.local.settings.AppSettingsRepository(context).getDriveFilterSnapshot()
        val parentBookId = database.notesDao().getPageById(note.pageId)?.bookId
        if (!driveFilter.isNoteSynced(note.id, parentBookId)) return@withContext note

        // We need the token
        val token = acquireDriveAccessToken(email, emitSyncErrors = false)
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

        val token = acquireDriveAccessToken(email, emitSyncErrors = false)
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

    suspend fun createDriveDatabaseStructure(forceRetryGms: Boolean = false): Result<String> = withContext(Dispatchers.IO) {
        val email = _userEmail.value
        if (email.isNullOrEmpty() || email == "offline") {
            val msg = "Debes iniciar sesión con una cuenta de Google para crear la estructura en Google Drive."
            _syncState.value = SyncState.Error(msg)
            return@withContext Result.failure(Exception(msg))
        }

        val token = acquireDriveAccessToken(
            email = email,
            emitSyncErrors = true,
            allowInteractiveAuth = true,
            forceRetryGms = forceRetryGms
        )
        if (token == null) {
            return@withContext Result.failure(Exception("Autorización pendiente o rechazada para Google Drive."))
        }

        val dao = database.notesDao()
        val driveFilter = com.example.data.local.settings.AppSettingsRepository(context).getDriveFilterSnapshot()
        val books = dao.getAllBooks(email).filter { driveFilter.isBookSynced(it.id) }
        val allowedBookIds = books.map { it.id }.toHashSet()
        val pages = mutableListOf<PageEntity>()
        for (book in books) {
            pages.addAll(dao.getPagesForBook(book.id))
        }
        val pageToBookId = pages.associate { it.id to it.bookId }
        val notes = dao.getAllNotes(email).filter { note ->
            val parentBookId = pageToBookId[note.pageId]
            val bookAllowed = parentBookId == null || parentBookId in allowedBookIds
            bookAllowed && driveFilter.isNoteSynced(note.id, parentBookId)
        }
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

    private suspend fun acquireDriveAccessToken(
        email: String,
        emitSyncErrors: Boolean,
        allowInteractiveAuth: Boolean = false,
        forceRetryGms: Boolean = false
    ): String? {
        val now = System.currentTimeMillis()
        val key = normalizeAccountKey(email)

        // 1. In-memory cache
        val cached = cachedAccessToken
        if (!cached.isNullOrBlank() &&
            cachedTokenEmail != null &&
            normalizeAccountKey(cachedTokenEmail!!) == key &&
            now < cachedTokenExpiresAtMs
        ) {
            return cached
        }

        // 2. Alternative Browser OAuth stored access / refresh token (bypasses GMS completely once authorized)
        val storedBrowserToken = prefs.getString("custom_oauth_access_token_$key", null)
        val storedBrowserExpires = prefs.getLong("custom_oauth_expires_$key", 0L)
        if (!storedBrowserToken.isNullOrBlank() && now < storedBrowserExpires - 30_000L) {
            cachedAccessToken = storedBrowserToken
            cachedTokenEmail = email
            cachedTokenExpiresAtMs = storedBrowserExpires
            _gmsAuthDiagnostic.value = null
            return storedBrowserToken
        }
        val storedRefresh = prefs.getString("custom_oauth_refresh_token_$key", null)
        if (!storedRefresh.isNullOrBlank()) {
            val refreshed = refreshBrowserOAuthToken(email, storedRefresh)
            if (!refreshed.isNullOrBlank()) {
                _gmsAuthDiagnostic.value = null
                return refreshed
            }
        }

        if (forceRetryGms) {
            gmsBlockedAccounts.remove(key)
            _gmsAuthDiagnostic.value = null
        }

        // Prevent repeated 1-second GMS popup flashes once Play Services has rejected/canceled this APK
        if (isGmsBlockedForAccount(email)) {
            if (emitSyncErrors) {
                val diag = _gmsAuthDiagnostic.value ?: buildGmsFailureDiagnostic(
                    email = email,
                    resultCode = null,
                    statusCode = null,
                    rawDetails = null
                )
                _gmsAuthDiagnostic.value = diag
                _syncState.value = SyncState.Error(diag)
            }
            return null
        }

        val account = Account(email, "com.google")
        val requestedScopes = listOf(
            Scope("https://www.googleapis.com/auth/drive.file"),
            Scope("https://www.googleapis.com/auth/drive.appdata")
        )

        var lastAuthError: Exception? = null
        try {
            val authRequest = AuthorizationRequest.builder()
                .setRequestedScopes(requestedScopes)
                .setAccount(account)
                .build()
            val authResult = suspendCancellableCoroutine { cont ->
                Identity.getAuthorizationClient(context)
                    .authorize(authRequest)
                    .addOnSuccessListener { result ->
                        if (cont.isActive) cont.resume(result)
                    }
                    .addOnFailureListener { ex ->
                        if (cont.isActive) cont.resumeWithException(ex)
                    }
            }
            if (authResult.hasResolution() && authResult.pendingIntent != null) {
                if (allowInteractiveAuth) {
                    _pendingAuthIntentSender.value = authResult.pendingIntent!!.intentSender
                    if (emitSyncErrors) {
                        _syncState.value = SyncState.Error(
                            "Concede acceso a Google Drive en la ventana de autorización para continuar."
                        )
                    }
                }
                return null
            }
            val token = authResult.accessToken
            if (!token.isNullOrBlank()) {
                cachedAccessToken = token
                cachedTokenEmail = email
                cachedTokenExpiresAtMs = System.currentTimeMillis() + 45L * 60_000L
                _gmsAuthDiagnostic.value = null
                return token
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            lastAuthError = e
            Log.w("DriveSyncManager", "AuthorizationClient fallback for $email: ${e.message}")
        }

        // Fallback via GoogleAuthUtil if AuthorizationClient is unavailable or rejected (non-signature errors)
        val rawFirstMsg = lastAuthError?.localizedMessage.orEmpty()
        val isSignatureOrConsoleErr = rawFirstMsg.contains("UnregisteredOnApiConsole", ignoreCase = true) ||
            (lastAuthError as? ApiException)?.statusCode == 10
        if (!isSignatureOrConsoleErr) {
            val primaryScope = "oauth2:https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive.appdata"
            val fallbackScope = "oauth2:https://www.googleapis.com/auth/drive.appdata"
            try {
                val token = GoogleAuthUtil.getToken(context, account, primaryScope)
                if (token.isNotBlank()) {
                    cachedAccessToken = token
                    cachedTokenEmail = email
                    cachedTokenExpiresAtMs = System.currentTimeMillis() + 45L * 60_000L
                    _gmsAuthDiagnostic.value = null
                    return token
                }
            } catch (recoverable: com.google.android.gms.auth.UserRecoverableAuthException) {
                if (allowInteractiveAuth) {
                    _recoveryIntent.value = recoverable.intent
                    if (emitSyncErrors) {
                        _syncState.value = SyncState.Error("Se requiere permiso para acceder a Google Drive. Por favor concede la autorización.")
                    }
                }
                return null
            } catch (_: Exception) {
                try {
                    val token = GoogleAuthUtil.getToken(context, account, fallbackScope)
                    if (token.isNotBlank()) {
                        cachedAccessToken = token
                        cachedTokenEmail = email
                        cachedTokenExpiresAtMs = System.currentTimeMillis() + 45L * 60_000L
                        _gmsAuthDiagnostic.value = null
                        return token
                    }
                } catch (recoverable2: com.google.android.gms.auth.UserRecoverableAuthException) {
                    if (allowInteractiveAuth) {
                        _recoveryIntent.value = recoverable2.intent
                        if (emitSyncErrors) {
                            _syncState.value = SyncState.Error("Se requiere autorización para Google Drive.")
                        }
                    }
                    return null
                } catch (authEx2: Exception) {
                    lastAuthError = authEx2
                }
            }
        }

        // Cache block for this account so Play Services doesn't keep flashing on background saves
        gmsBlockedAccounts.add(key)
        val statusCode = (lastAuthError as? ApiException)?.statusCode
        val diag = buildGmsFailureDiagnostic(
            email = email,
            resultCode = null,
            statusCode = statusCode,
            rawDetails = lastAuthError?.localizedMessage
        )
        _gmsAuthDiagnostic.value = diag
        if (emitSyncErrors) {
            _syncState.value = SyncState.Error(diag)
        }
        return null
    }

    private fun buildGmsFailureDiagnostic(
        email: String,
        resultCode: Int?,
        statusCode: Int?,
        rawDetails: String?
    ): String {
        val sha1 = getAppSha1Fingerprint().ifBlank { "No disponible" }
        val detailNote = when {
            rawDetails?.contains("UnregisteredOnApiConsole", ignoreCase = true) == true || statusCode == 10 ->
                "Cliente OAuth de Android no registrado para esta firma (DEVELOPER_ERROR / UnregisteredOnApiConsole)."
            resultCode == Activity.RESULT_CANCELED ->
                "Google Play Services cerró o rechazó la ventana de autorización en 1 s. Esto ocurre cuando el APK (compilado en GitHub Workflow) tiene una huella SHA-1 distinta a la registrada en Google Cloud."
            !rawDetails.isNullOrBlank() -> rawDetails
            else -> "Google Play Services no pudo completar la autorización automática de Drive."
        }
        return buildString {
            append(detailNote)
            append("\n• Paquete: ${context.packageName}")
            append("\n• SHA-1 APK: $sha1")
            append("\n• Solución sin cerrar la app: usa «OAuth Navegador» abajo, exporta respaldo JSON o registra este SHA-1 en Google Cloud Console. Tus notas en $email siguen guardándose localmente.")
        }
    }

    suspend fun launchAlternativeBrowserOAuth(
        clientIdInput: String,
        clientSecretInput: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val email = _userEmail.value
        if (email.isNullOrBlank() || email == "offline") {
            return@withContext Result.failure(IllegalStateException("Inicia sesión con una cuenta de Google antes de autorizar Drive."))
        }
        val cleanClientId = clientIdInput.trim().ifBlank { DEFAULT_WEB_OAUTH_CLIENT_ID }
        val cleanSecret = clientSecretInput.trim()
        saveCustomOAuthConfig(cleanClientId, cleanSecret)

        runCatching { activeOAuthSocket?.close() }
        val serverSocket = try {
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply {
                soTimeout = 180_000
            }
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("No se pudo iniciar el servidor local OAuth: ${e.localizedMessage}"))
        }
        activeOAuthSocket = serverSocket

        return@withContext try {
            val port = serverSocket.localPort
            val redirectUri = "http://127.0.0.1:$port"
            val codeVerifier = generatePkceCodeVerifier()
            val codeChallenge = computePkceCodeChallenge(codeVerifier)
            val state = UUID.randomUUID().toString()

            val authUrl = buildBrowserOAuthUrl(
                clientId = cleanClientId,
                redirectUri = redirectUri,
                codeChallenge = codeChallenge,
                state = state,
                loginHint = email
            )

            _syncState.value = SyncState.Syncing
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            _browserAuthIntent.value = intent

            var authCode: String? = null
            var authError: String? = null
            var returnedState: String? = null

            serverSocket.accept().use { socket ->
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val requestLine = reader.readLine().orEmpty()
                val queryPart = requestLine.substringAfter("GET ", "")
                    .substringBefore(" HTTP/")
                    .substringAfter("?", "")
                if (queryPart.isNotEmpty()) {
                    for (pair in queryPart.split("&")) {
                        val idx = pair.indexOf('=')
                        if (idx > 0) {
                            val k = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                            val v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                            when (k) {
                                "code" -> authCode = v
                                "error" -> authError = v
                                "state" -> returnedState = v
                            }
                        }
                    }
                }

                val html = if (!authCode.isNullOrBlank() && returnedState == state) {
                    "<html><body style='background:#0E0B18;color:#F2ECFF;font-family:sans-serif;text-align:center;padding:48px'><h2>¡Autorizaci&oacute;n completada!</h2><p>Ya puedes volver a UTK Notes para sincronizar con Google Drive.</p></body></html>"
                } else {
                    "<html><body style='background:#0E0B18;color:#FFB4AB;font-family:sans-serif;text-align:center;padding:48px'><h2>Autorizaci&oacute;n cancelada</h2><p>Detalle: ${authError ?: "No se recibi&oacute; c&oacute;digo de acceso"}.</p><p>Puedes regresar a UTK Notes.</p></body></html>"
                }
                val httpResp = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\nConnection: close\r\n\r\n$html"
                socket.getOutputStream().write(httpResp.toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()
            }

            if (!authError.isNullOrBlank()) {
                val msg = "OAuth navegador rechazado ($authError). Verifica el Client ID de tipo 'Aplicación de escritorio' en Google Cloud."
                _gmsAuthDiagnostic.value = msg
                _syncState.value = SyncState.Error(msg)
                return@withContext Result.failure(Exception(msg))
            }
            if (authCode.isNullOrBlank() || returnedState != state) {
                val msg = "No se completó la autorización en el navegador."
                _syncState.value = SyncState.Error(msg)
                return@withContext Result.failure(Exception(msg))
            }

            val tokenBodyBuilder = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", authCode!!)
                .add("client_id", cleanClientId)
                .add("redirect_uri", redirectUri)
                .add("code_verifier", codeVerifier)
            if (cleanSecret.isNotEmpty()) {
                tokenBodyBuilder.add("client_secret", cleanSecret)
            }

            val tokenRequest = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(tokenBodyBuilder.build())
                .build()

            client.newCall(tokenRequest).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val errDesc = runCatching { JSONObject(rawBody).optString("error_description") }
                        .getOrNull().orEmpty().ifBlank { rawBody.take(180) }
                    val msg = "No se pudo canjear el token OAuth (${resp.code}): $errDesc. Si aparece redirect_uri_mismatch o invalid_client, crea un Client ID 'Desktop app' en Google Cloud e ingrésalo aquí."
                    _gmsAuthDiagnostic.value = msg
                    _syncState.value = SyncState.Error(msg)
                    return@withContext Result.failure(Exception(msg))
                }
                val json = JSONObject(rawBody)
                val accessToken = json.optString("access_token")
                if (accessToken.isBlank()) {
                    return@withContext Result.failure(Exception("Google no devolvió un access_token."))
                }
                val expiresInSec = json.optLong("expires_in", 3600L).coerceAtLeast(120L)
                val refreshToken = json.optString("refresh_token")
                val expiresAtMs = System.currentTimeMillis() + expiresInSec * 1000L
                val key = normalizeAccountKey(email)

                prefs.edit().apply {
                    putString("custom_oauth_access_token_$key", accessToken)
                    putLong("custom_oauth_expires_$key", expiresAtMs)
                    if (refreshToken.isNotBlank()) {
                        putString("custom_oauth_refresh_token_$key", refreshToken)
                    }
                }.commit()

                cachedAccessToken = accessToken
                cachedTokenEmail = email
                cachedTokenExpiresAtMs = expiresAtMs
                gmsBlockedAccounts.remove(key)
                _gmsAuthDiagnostic.value = null
            }

            synchronize(allowInteractiveAuth = false, forceRetryGms = false)
            Result.success("Google Drive autorizado mediante navegador para $email")
        } catch (e: Exception) {
            val msg = "OAuth en navegador no completado: ${e.localizedMessage ?: "Tiempo agotado o cancelado"}"
            _syncState.value = SyncState.Error(msg)
            Result.failure(Exception(msg))
        } finally {
            runCatching { serverSocket.close() }
            if (activeOAuthSocket === serverSocket) {
                activeOAuthSocket = null
            }
        }
    }

    private fun refreshBrowserOAuthToken(email: String, refreshToken: String): String? {
        val clientId = getSavedCustomOAuthClientId()
        val clientSecret = getSavedCustomOAuthClientSecret()
        return try {
            val form = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", clientId)
                .apply {
                    if (clientSecret.isNotBlank()) add("client_secret", clientSecret)
                }
                .build()
            val req = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(form)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val json = JSONObject(resp.body?.string().orEmpty())
                val newAccess = json.optString("access_token")
                if (newAccess.isBlank()) return null
                val expiresAtMs = System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L
                val key = normalizeAccountKey(email)
                prefs.edit()
                    .putString("custom_oauth_access_token_$key", newAccess)
                    .putLong("custom_oauth_expires_$key", expiresAtMs)
                    .commit()
                cachedAccessToken = newAccess
                cachedTokenEmail = email
                cachedTokenExpiresAtMs = expiresAtMs
                newAccess
            }
        } catch (e: Exception) {
            Log.w("DriveSyncManager", "Error refreshing browser OAuth token", e)
            null
        }
    }

    suspend fun exportAccountBackupToUri(accountId: String, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanAccount = accountId.trim().ifBlank { "offline" }
            val dao = database.notesDao()
            val books = dao.getAllBooks(cleanAccount)
            val pages = mutableListOf<PageEntity>()
            for (b in books) pages.addAll(dao.getPagesForBook(b.id))
            val notes = mutableListOf<NoteEntity>()
            for (p in pages) notes.addAll(dao.getNotesForPage(p.id))
            val chats = dao.getAllChatSessions(cleanAccount)
            val payload = DriveBackupPayload(
                books = books,
                pages = pages,
                notes = notes,
                chatSessions = chats,
                apiKey = null,
                timestamp = System.currentTimeMillis()
            )
            val json = moshi.adapter(DriveBackupPayload::class.java).toJson(payload)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: return@withContext Result.failure(Exception("No se pudo abrir el archivo destino."))

            val label = if (cleanAccount == "offline") "Modo Local" else cleanAccount
            Result.success("Respaldo exportado ($label): ${books.size} libros y ${notes.size} notas.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importAccountBackupFromUri(targetAccountId: String, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanAccount = targetAccountId.trim().ifBlank { "offline" }
            registerKnownAccount(cleanAccount)
            val jsonText = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().readText()
            }.orEmpty()
            if (jsonText.isBlank()) {
                return@withContext Result.failure(Exception("El archivo seleccionado está vacío."))
            }
            val payload = moshi.adapter(DriveBackupPayload::class.java).fromJson(jsonText)
                ?: return@withContext Result.failure(Exception("Formato de respaldo JSON inválido."))
            mergeBackup(payload, cleanAccount)
            val label = if (cleanAccount == "offline") "Modo Local" else cleanAccount
            Result.success("Importados en $label: ${payload.books.size} libros, ${payload.pages.size} páginas y ${payload.notes.size} notas.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Suppress("DEPRECATION")
    private fun getAppSha1Fingerprint(): String {
        return try {
            val pkgInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }
            val certBytes = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pkgInfo.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            } else {
                pkgInfo.signatures?.firstOrNull()?.toByteArray()
            } ?: return ""
            val digest = MessageDigest.getInstance("SHA-1").digest(certBytes)
            digest.joinToString(":") { "%02X".format(it) }
        } catch (_: Exception) {
            ""
        }
    }
}
