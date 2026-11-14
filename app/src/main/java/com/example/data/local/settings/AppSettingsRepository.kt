package com.example.data.local.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.example.data.model.BookEntity
import com.example.data.model.NoteEntity
import com.example.data.model.PageEntity
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.AppThemeId
import com.example.ui.theme.ColorSchemeMode
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.util.UUID

data class AppAppearanceState(
    val themeId: AppThemeId = AppThemeId.LAVANDA,
    val colorSchemeMode: ColorSchemeMode = ColorSchemeMode.DARK,
    val pureBlackDarkMode: Boolean = false,
    val fontScale: Float = 1.0f,
    val appLanguage: AppLanguage = AppLanguage.SPANISH
)

data class AiBehaviorState(
    val memoryEnabled: Boolean = true,
    val globalMemories: List<String> = emptyList(),
    val aboutUser: String = "",
    val maxOutputTokens: Int = 2048,
    val temperature: Float = 0.7f,
    val historyTurnsLimit: Int = 8
)

data class DriveFilterState(
    val excludedBookIds: Set<String> = emptySet(),
    val excludedNoteIds: Set<String> = emptySet()
) {
    fun isBookSynced(bookId: String): Boolean = bookId !in excludedBookIds
    fun isNoteSynced(noteId: String, parentBookId: String? = null): Boolean {
        if (parentBookId != null && parentBookId in excludedBookIds) return false
        return noteId !in excludedNoteIds
    }
}

@JsonClass(generateAdapter = true)
data class CrashReportEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val threadName: String = "main",
    val message: String = "",
    val stackTrace: String = "",
    val appVersion: String = "1.9.01",
    val engineLabel: String = ""
)

data class NoteStorageItem(
    val note: NoteEntity,
    val bookId: String,
    val bookTitle: String,
    val sizeBytes: Long,
    val isDriveSyncEnabled: Boolean
)

data class BookStorageItem(
    val book: BookEntity,
    val notes: List<NoteStorageItem>,
    val totalBytes: Long,
    val isDriveSyncEnabled: Boolean
)

data class StorageOverviewMetrics(
    val totalLibraryBytes: Long = 0L,
    val totalAttachmentsBytes: Long = 0L,
    val totalModelsBytes: Long = 0L,
    val totalBackupsBytes: Long = 0L,
    val totalCrashLogsBytes: Long = 0L,
    val books: List<BookStorageItem> = emptyList()
)

class AppSettingsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _appearanceState = MutableStateFlow(loadAppearance())
    val appearanceState: StateFlow<AppAppearanceState> = _appearanceState.asStateFlow()

    private val _aiBehaviorState = MutableStateFlow(loadAiBehavior())
    val aiBehaviorState: StateFlow<AiBehaviorState> = _aiBehaviorState.asStateFlow()

    private val _driveFilterState = MutableStateFlow(loadDriveFilters())
    val driveFilterState: StateFlow<DriveFilterState> = _driveFilterState.asStateFlow()

    private fun loadAppearance(): AppAppearanceState {
        val themeRaw = prefs.getString(KEY_THEME_ID, AppThemeId.LAVANDA.id)
        val themeId = AppThemeId.entries.firstOrNull { it.id == themeRaw } ?: AppThemeId.LAVANDA

        val modeRaw = prefs.getString(KEY_COLOR_MODE, ColorSchemeMode.DARK.id)
        val mode = ColorSchemeMode.entries.firstOrNull { it.id == modeRaw } ?: ColorSchemeMode.DARK

        val pureBlack = prefs.getBoolean(KEY_PURE_BLACK, false)
        val fontScale = prefs.getFloat(KEY_FONT_SCALE, 1.0f).coerceIn(0.85f, 1.35f)
        val lang = AppLanguage.fromCode(prefs.getString(KEY_LANGUAGE, AppLanguage.SPANISH.code))

        return AppAppearanceState(
            themeId = themeId,
            colorSchemeMode = mode,
            pureBlackDarkMode = pureBlack,
            fontScale = fontScale,
            appLanguage = lang
        )
    }

    fun setThemeId(themeId: AppThemeId) {
        prefs.edit().putString(KEY_THEME_ID, themeId.id).apply()
        _appearanceState.value = _appearanceState.value.copy(themeId = themeId)
    }

    fun setColorSchemeMode(mode: ColorSchemeMode) {
        prefs.edit().putString(KEY_COLOR_MODE, mode.id).apply()
        _appearanceState.value = _appearanceState.value.copy(colorSchemeMode = mode)
    }

    fun setPureBlackDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PURE_BLACK, enabled).apply()
        _appearanceState.value = _appearanceState.value.copy(pureBlackDarkMode = enabled)
    }

    fun setFontScale(scale: Float) {
        val clamped = scale.coerceIn(0.85f, 1.35f)
        prefs.edit().putFloat(KEY_FONT_SCALE, clamped).apply()
        _appearanceState.value = _appearanceState.value.copy(fontScale = clamped)
    }

    fun setAppLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.code).apply()
        _appearanceState.value = _appearanceState.value.copy(appLanguage = language)
    }

    private fun loadAiBehavior(): AiBehaviorState {
        val memoryEnabled = prefs.getBoolean(KEY_AI_MEMORY_ENABLED, true)
        val memoriesRaw = prefs.getString(KEY_AI_GLOBAL_MEMORIES, null)
        val memories = decodeStringList(memoriesRaw)
        val aboutUser = prefs.getString(KEY_AI_ABOUT_USER, "").orEmpty()
        val maxTokens = prefs.getInt(KEY_AI_MAX_TOKENS, 2048).coerceIn(256, 8192)
        val temperature = prefs.getFloat(KEY_AI_TEMPERATURE, 0.7f).coerceIn(0.0f, 1.5f)
        val historyLimit = prefs.getInt(KEY_AI_HISTORY_LIMIT, 8).coerceIn(0, 20)
        return AiBehaviorState(
            memoryEnabled = memoryEnabled,
            globalMemories = memories,
            aboutUser = aboutUser,
            maxOutputTokens = maxTokens,
            temperature = temperature,
            historyTurnsLimit = historyLimit
        )
    }

    fun setAiMemoryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AI_MEMORY_ENABLED, enabled).apply()
        _aiBehaviorState.value = _aiBehaviorState.value.copy(memoryEnabled = enabled)
    }

    fun addGlobalMemory(fact: String) {
        val cleaned = fact.trim()
        if (cleaned.isEmpty()) return
        val current = _aiBehaviorState.value.globalMemories
        if (current.any { it.equals(cleaned, ignoreCase = true) }) return
        val updated = current + cleaned
        prefs.edit().putString(KEY_AI_GLOBAL_MEMORIES, encodeStringList(updated)).apply()
        _aiBehaviorState.value = _aiBehaviorState.value.copy(globalMemories = updated)
    }

    fun removeGlobalMemory(index: Int) {
        val current = _aiBehaviorState.value.globalMemories
        if (index !in current.indices) return
        val updated = current.toMutableList().apply { removeAt(index) }
        prefs.edit().putString(KEY_AI_GLOBAL_MEMORIES, encodeStringList(updated)).apply()
        _aiBehaviorState.value = _aiBehaviorState.value.copy(globalMemories = updated)
    }

    fun clearGlobalMemories() {
        prefs.edit().remove(KEY_AI_GLOBAL_MEMORIES).apply()
        _aiBehaviorState.value = _aiBehaviorState.value.copy(globalMemories = emptyList())
    }

    fun setAboutUser(about: String) {
        val trimmed = about.take(1500)
        prefs.edit().putString(KEY_AI_ABOUT_USER, trimmed).apply()
        _aiBehaviorState.value = _aiBehaviorState.value.copy(aboutUser = trimmed)
    }

    fun setMaxOutputTokens(tokens: Int) {
        val clamped = tokens.coerceIn(256, 8192)
        prefs.edit().putInt(KEY_AI_MAX_TOKENS, clamped).apply()
        _aiBehaviorState.value = _aiBehaviorState.value.copy(maxOutputTokens = clamped)
    }

    fun setTemperature(temp: Float) {
        val clamped = temp.coerceIn(0.0f, 1.5f)
        prefs.edit().putFloat(KEY_AI_TEMPERATURE, clamped).apply()
        _aiBehaviorState.value = _aiBehaviorState.value.copy(temperature = clamped)
    }

    fun setHistoryTurnsLimit(limit: Int) {
        val clamped = limit.coerceIn(0, 20)
        prefs.edit().putInt(KEY_AI_HISTORY_LIMIT, clamped).apply()
        _aiBehaviorState.value = _aiBehaviorState.value.copy(historyTurnsLimit = clamped)
    }

    private fun loadDriveFilters(): DriveFilterState {
        val books = prefs.getStringSet(KEY_EXCLUDED_DRIVE_BOOKS, emptySet())?.toSet().orEmpty()
        val notes = prefs.getStringSet(KEY_EXCLUDED_DRIVE_NOTES, emptySet())?.toSet().orEmpty()
        return DriveFilterState(
            excludedBookIds = books,
            excludedNoteIds = notes
        )
    }

    fun setBookDriveSyncEnabled(bookId: String, enabled: Boolean) {
        val updated = _driveFilterState.value.excludedBookIds.toMutableSet().apply {
            if (enabled) remove(bookId) else add(bookId)
        }.toSet()
        prefs.edit().putStringSet(KEY_EXCLUDED_DRIVE_BOOKS, updated).apply()
        _driveFilterState.value = _driveFilterState.value.copy(excludedBookIds = updated)
    }

    fun setNoteDriveSyncEnabled(noteId: String, enabled: Boolean) {
        val updated = _driveFilterState.value.excludedNoteIds.toMutableSet().apply {
            if (enabled) remove(noteId) else add(noteId)
        }.toSet()
        prefs.edit().putStringSet(KEY_EXCLUDED_DRIVE_NOTES, updated).apply()
        _driveFilterState.value = _driveFilterState.value.copy(excludedNoteIds = updated)
    }

    fun getDriveFilterSnapshot(): DriveFilterState = loadDriveFilters()

    private fun encodeStringList(items: List<String>): String {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        return arr.toString()
    }

    private fun decodeStringList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val value = arr.optString(i).trim()
                    if (value.isNotEmpty()) add(value)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        const val PREFS_NAME = "utk_app_settings_v2"
        private const val KEY_THEME_ID = "appearance_theme_id"
        private const val KEY_COLOR_MODE = "appearance_color_mode"
        private const val KEY_PURE_BLACK = "appearance_pure_black"
        private const val KEY_FONT_SCALE = "appearance_font_scale"
        private const val KEY_LANGUAGE = "appearance_language"

        private const val KEY_AI_MEMORY_ENABLED = "ai_memory_enabled"
        private const val KEY_AI_GLOBAL_MEMORIES = "ai_global_memories_json"
        private const val KEY_AI_ABOUT_USER = "ai_about_user"
        private const val KEY_AI_MAX_TOKENS = "ai_max_output_tokens"
        private const val KEY_AI_TEMPERATURE = "ai_temperature"
        private const val KEY_AI_HISTORY_LIMIT = "ai_history_turns_limit"

        private const val KEY_EXCLUDED_DRIVE_BOOKS = "drive_excluded_books"
        private const val KEY_EXCLUDED_DRIVE_NOTES = "drive_excluded_notes"
    }
}

class CrashReportManager(context: Context) {

    private val appContext = context.applicationContext
    private val reportFile: File = File(appContext.filesDir, CRASH_REPORTS_FILE_NAME)

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val listAdapter = moshi.adapter<List<CrashReportEntry>>(
        Types.newParameterizedType(List::class.java, CrashReportEntry::class.java)
    )

    private val _reports = MutableStateFlow(readReportsFromDisk())
    val reports: StateFlow<List<CrashReportEntry>> = _reports.asStateFlow()

    @Volatile
    private var engineLabelProvider: () -> String = { "Unknown" }

    fun installUncaughtHandler(engineLabelProvider: () -> String) {
        this.engineLabelProvider = engineLabelProvider
        synchronized(handlerLock) {
            if (isInstalled) return
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    recordException(
                        source = "UncaughtException (${thread.name})",
                        throwable = throwable,
                        threadName = thread.name,
                        engineLabel = engineLabelProvider()
                    )
                } catch (_: Throwable) {
                } finally {
                    previous?.uncaughtException(thread, throwable)
                }
            }
            isInstalled = true
        }
    }

    fun recordException(
        source: String,
        throwable: Throwable,
        threadName: String = Thread.currentThread().name,
        engineLabel: String = engineLabelProvider()
    ) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val entry = CrashReportEntry(
            timestamp = System.currentTimeMillis(),
            threadName = threadName,
            message = "[$source] ${throwable.javaClass.simpleName}: ${throwable.message.orEmpty()}",
            stackTrace = sw.toString().take(8_000),
            appVersion = resolveAppVersion(),
            engineLabel = engineLabel
        )
        appendEntry(entry)
    }

    fun recordDiagnosticEntry(title: String, detail: String, engineLabel: String = engineLabelProvider()) {
        val entry = CrashReportEntry(
            timestamp = System.currentTimeMillis(),
            threadName = Thread.currentThread().name,
            message = title,
            stackTrace = detail,
            appVersion = resolveAppVersion(),
            engineLabel = engineLabel
        )
        appendEntry(entry)
    }

    private fun appendEntry(entry: CrashReportEntry) {
        synchronized(fileLock) {
            val updated = (listOf(entry) + readReportsFromDisk()).take(MAX_REPORTS)
            writeReportsToDisk(updated)
            _reports.value = updated
        }
    }

    fun clearReports() {
        synchronized(fileLock) {
            runCatching {
                if (reportFile.exists()) reportFile.delete()
            }
            _reports.value = emptyList()
        }
    }

    fun getReportsFileSizeBytes(): Long {
        return if (reportFile.exists()) reportFile.length() else 0L
    }

    fun formatReportForShare(entry: CrashReportEntry): String {
        return buildString {
            appendLine("=== UTK Notes Crash / Diagnostic Report ===")
            appendLine("ID: ${entry.id}")
            appendLine("Timestamp: ${entry.timestamp}")
            appendLine("Version: ${entry.appVersion}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")
            appendLine("Thread: ${entry.threadName}")
            if (entry.engineLabel.isNotBlank()) appendLine("Engine: ${entry.engineLabel}")
            appendLine("Message: ${entry.message}")
            appendLine("--- Stacktrace / Detail ---")
            appendLine(entry.stackTrace)
        }
    }

    private fun resolveAppVersion(): String {
        return try {
            val pInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            pInfo.versionName ?: "1.9.01"
        } catch (_: Exception) {
            "1.9.01"
        }
    }

    private fun readReportsFromDisk(): List<CrashReportEntry> {
        return try {
            if (!reportFile.exists()) return emptyList()
            val raw = reportFile.readText(StandardCharsets.UTF_8)
            if (raw.isBlank()) return emptyList()
            listAdapter.fromJson(raw).orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to parse crash reports", e)
            emptyList()
        }
    }

    private fun writeReportsToDisk(items: List<CrashReportEntry>) {
        try {
            reportFile.writeText(listAdapter.toJson(items), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to persist crash reports", e)
        }
    }

    companion object {
        private const val TAG = "CrashReportManager"
        private const val CRASH_REPORTS_FILE_NAME = "crash_reports.json"
        private const val MAX_REPORTS = 40
        private val fileLock = Any()
        private val handlerLock = Any()
        @Volatile
        private var isInstalled = false
    }
}

object StorageMetricsCalculator {

    private val localMediaRegex = Regex("""(urlOrPath|sourceUrl|filePath)"\s*:\s*"([^"]+)"""")

    fun computeMetrics(
        context: Context,
        books: List<BookEntity>,
        pages: List<PageEntity>,
        notes: List<NoteEntity>,
        filterState: DriveFilterState,
        crashLogsBytes: Long
    ): StorageOverviewMetrics {
        val pageToBookId = pages.associate { it.id to it.bookId }
        val activeBooks = books.filter { !it.isDeleted }
        val activeNotes = notes.filter { !it.isDeleted }

        var totalAttachBytes = 0L
        val countedFiles = HashSet<String>()

        val noteItemsByBook = HashMap<String, MutableList<NoteStorageItem>>()
        val bookMap = activeBooks.associateBy { it.id }

        for (note in activeNotes) {
            val bookId = pageToBookId[note.pageId] ?: "orphan"
            val bookTitle = bookMap[bookId]?.title ?: "Sin libro"
            val textBytes = (note.title.toByteArray(StandardCharsets.UTF_8).size +
                note.content.toByteArray(StandardCharsets.UTF_8).size +
                note.tags.toByteArray(StandardCharsets.UTF_8).size +
                note.attachments.toByteArray(StandardCharsets.UTF_8).size).toLong()

            var attachBytes = 0L
            localMediaRegex.findAll(note.content).forEach { match ->
                val path = match.groupValues.getOrNull(2).orEmpty()
                if (path.startsWith("/") || path.startsWith("file://")) {
                    val clean = path.removePrefix("file://")
                    val f = File(clean)
                    if (f.exists() && f.isFile) {
                        val size = f.length()
                        attachBytes += size
                        if (countedFiles.add(f.absolutePath)) {
                            totalAttachBytes += size
                        }
                    }
                }
            }

            val noteVaultDir = File(context.filesDir, "notes_vault/note_${note.id.replace("[^a-zA-Z0-9-_]".toRegex(), "_")}")
            if (noteVaultDir.exists()) {
                attachBytes += directorySizeBytes(noteVaultDir)
            }

            val totalNoteSize = textBytes + attachBytes
            val isNoteSyncOn = filterState.isNoteSynced(note.id, parentBookId = bookId)
            val list = noteItemsByBook.getOrPut(bookId) { mutableListOf() }
            list.add(
                NoteStorageItem(
                    note = note,
                    bookId = bookId,
                    bookTitle = bookTitle,
                    sizeBytes = totalNoteSize,
                    isDriveSyncEnabled = isNoteSyncOn
                )
            )
        }

        val mediaDir = File(context.filesDir, "note_media")
        if (mediaDir.exists()) {
            mediaDir.walkTopDown().filter { it.isFile }.forEach { f ->
                if (countedFiles.add(f.absolutePath)) {
                    totalAttachBytes += f.length()
                }
            }
        }

        val bookItems = activeBooks.map { book ->
            val coverBytes = book.coverUri?.takeIf { it.startsWith("/") || it.startsWith("file://") }?.let { raw ->
                val f = File(raw.removePrefix("file://"))
                if (f.exists() && f.isFile) f.length() else 0L
            } ?: 0L
            val metaBytes = book.title.toByteArray(StandardCharsets.UTF_8).size.toLong() + coverBytes
            val bookNotes = noteItemsByBook[book.id].orEmpty().sortedByDescending { it.sizeBytes }
            val totalBookBytes = metaBytes + bookNotes.sumOf { it.sizeBytes }
            BookStorageItem(
                book = book,
                notes = bookNotes,
                totalBytes = totalBookBytes,
                isDriveSyncEnabled = filterState.isBookSynced(book.id)
            )
        }.sortedByDescending { it.totalBytes }

        val modelsDir = File(context.filesDir, "llm_models")
        val modelsBytes = directorySizeBytes(modelsDir) +
            (context.filesDir.listFiles()?.filter { it.isFile && it.name.endsWith(".gguf", ignoreCase = true) }?.sumOf { it.length() } ?: 0L)

        val backupFilesBytes = context.getExternalFilesDir(null)
            ?.listFiles()
            ?.filter { it.isFile && it.name.startsWith("utk_notes_backup_") }
            ?.sumOf { it.length() } ?: 0L

        val totalLibBytes = bookItems.sumOf { it.totalBytes }
        return StorageOverviewMetrics(
            totalLibraryBytes = totalLibBytes,
            totalAttachmentsBytes = totalAttachBytes,
            totalModelsBytes = modelsBytes,
            totalBackupsBytes = backupFilesBytes,
            totalCrashLogsBytes = crashLogsBytes,
            books = bookItems
        )
    }

    private fun directorySizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun formatByteSize(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format("%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.2f GB", gb)
    }
}
