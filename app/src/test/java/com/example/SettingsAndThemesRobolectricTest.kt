package com.example

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ai.AiApiSettingsState
import com.example.data.local.ai.ApiKeyValidationState
import com.example.data.local.llm.LlmModelState
import com.example.data.local.llm.ModelDownloadStatus
import com.example.data.local.settings.AppSettingsRepository
import com.example.data.local.settings.CrashReportManager
import com.example.data.local.settings.DriveFilterState
import com.example.data.local.settings.StorageMetricsCalculator
import com.example.data.model.BookEntity
import com.example.data.model.NoteEntity
import com.example.data.model.PageEntity
import com.example.data.remote.ai.ExternalAiProvider
import com.example.data.remote.ai.ExternalAiProviderClient
import com.example.ui.AetherViewModel
import com.example.ui.AiEngineType
import com.example.ui.resolveAuraActiveModelLabel
import com.example.ui.settings.SettingsHubScreen
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.AppThemeId
import com.example.ui.theme.ColorSchemeMode
import com.example.ui.theme.MyApplicationTheme
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsAndThemesRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(AppSettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `AppSettingsRepository defaults to Lavanda Dark Spanish and persists all settings`() {
        val repo = AppSettingsRepository(context)
        val initial = repo.appearanceState.value
        assertEquals(AppThemeId.LAVANDA, initial.themeId)
        assertEquals(ColorSchemeMode.DARK, initial.colorSchemeMode)
        assertFalse(initial.pureBlackDarkMode)
        assertEquals(1.0f, initial.fontScale, 0.001f)
        assertEquals(AppLanguage.SPANISH, initial.appLanguage)

        repo.setThemeId(AppThemeId.TOKYO_NIGHT)
        repo.setColorSchemeMode(ColorSchemeMode.LIGHT)
        repo.setPureBlackDarkMode(true)
        repo.setFontScale(1.8f) // Clamps to 1.35f
        repo.setAppLanguage(AppLanguage.ENGLISH)

        repo.setAiMemoryEnabled(false)
        repo.addGlobalMemory("Prefers concise answers")
        repo.addGlobalMemory("Speaks Spanish and English")
        repo.removeGlobalMemory(0)
        repo.setAboutUser("Medical student")
        repo.setMaxOutputTokens(4096)
        repo.setTemperature(0.4f)
        repo.setHistoryTurnsLimit(12)

        repo.setBookDriveSyncEnabled("book_1", false)
        repo.setNoteDriveSyncEnabled("note_2", false)

        val reloaded = AppSettingsRepository(context)
        assertEquals(AppThemeId.TOKYO_NIGHT, reloaded.appearanceState.value.themeId)
        assertEquals(ColorSchemeMode.LIGHT, reloaded.appearanceState.value.colorSchemeMode)
        assertTrue(reloaded.appearanceState.value.pureBlackDarkMode)
        assertEquals(1.35f, reloaded.appearanceState.value.fontScale, 0.001f)
        assertEquals(AppLanguage.ENGLISH, reloaded.appearanceState.value.appLanguage)

        assertFalse(reloaded.aiBehaviorState.value.memoryEnabled)
        assertEquals(listOf("Speaks Spanish and English"), reloaded.aiBehaviorState.value.globalMemories)
        assertEquals("Medical student", reloaded.aiBehaviorState.value.aboutUser)
        assertEquals(4096, reloaded.aiBehaviorState.value.maxOutputTokens)
        assertEquals(0.4f, reloaded.aiBehaviorState.value.temperature, 0.01f)
        assertEquals(12, reloaded.aiBehaviorState.value.historyTurnsLimit)

        assertFalse(reloaded.driveFilterState.value.isBookSynced("book_1"))
        assertFalse(reloaded.driveFilterState.value.isNoteSynced("note_in_excluded_book", parentBookId = "book_1"))
        assertFalse(reloaded.driveFilterState.value.isNoteSynced("note_2", parentBookId = "book_open"))
        assertTrue(reloaded.driveFilterState.value.isNoteSynced("note_3", parentBookId = "book_open"))
    }

    @Test
    fun `ExternalAiProviderClient formats payloads and parses Groq OpenAI and Claude responses`() {
        val client = ExternalAiProviderClient()

        val openAiJson = client.buildOpenAiCompatibleBody(
            model = "llama-3.3-70b-versatile",
            prompt = "Summarize notes",
            systemInstruction = "Be concise",
            imageBase64 = "abc123",
            imageMimeType = "image/png",
            history = listOf("user" to "Hi", "model" to "Hello"),
            maxTokens = 1500,
            temperature = 0.5f
        )
        val openAiObj = JSONObject(openAiJson)
        assertEquals("llama-3.3-70b-versatile", openAiObj.getString("model"))
        assertEquals(1500, openAiObj.getInt("max_tokens"))
        assertEquals(4, openAiObj.getJSONArray("messages").length())

        val claudeJson = client.buildClaudeBody(
            model = "claude-3-5-sonnet-latest",
            prompt = "Analyze text",
            systemInstruction = "Use Spanish",
            imageBase64 = null,
            imageMimeType = null,
            history = listOf("user" to "Previous"),
            maxTokens = 2048,
            temperature = 1.2f // clamped to 1.0 for Anthropic
        )
        val claudeObj = JSONObject(claudeJson)
        assertEquals("claude-3-5-sonnet-latest", claudeObj.getString("model"))
        assertEquals("Use Spanish", claudeObj.getString("system"))
        assertEquals(1.0, claudeObj.getDouble("temperature"), 0.01)

        val models = client.parseModelsResponse(
            ExternalAiProvider.OPENAI,
            """{"data":[{"id":"gpt-4o"},{"id":"gpt-4o-mini"}]}"""
        )
        assertEquals(listOf("gpt-4o", "gpt-4o-mini"), models.map { it.id })

        val groqText = client.extractCompletionText(
            ExternalAiProvider.GROQ,
            """{"choices":[{"message":{"content":"Respuesta desde Groq"}}]}"""
        )
        assertEquals("Respuesta desde Groq", groqText)

        val claudeText = client.extractCompletionText(
            ExternalAiProvider.CLAUDE,
            """{"content":[{"type":"text","text":"Respuesta desde Claude"}]}"""
        )
        assertEquals("Respuesta desde Claude", claudeText)
    }

    @Test
    fun `CrashReportManager records persists formats and clears crash entries`() {
        val manager = CrashReportManager(context)
        manager.clearReports()
        assertTrue(manager.reports.value.isEmpty())

        manager.recordException("TestHarness", IllegalStateException("Boom test"))
        manager.recordDiagnosticEntry("Manual Check", "Everything healthy", "GEMINI")

        val reloaded = CrashReportManager(context)
        assertEquals(2, reloaded.reports.value.size)
        val formatted = reloaded.formatReportForShare(reloaded.reports.value.first())
        assertTrue(formatted.contains("Manual Check"))
        assertTrue(formatted.contains("GEMINI"))

        reloaded.clearReports()
        assertTrue(CrashReportManager(context).reports.value.isEmpty())
    }

    @Test
    fun `resolveAuraActiveModelLabel supports Groq OpenAI and Claude states`() {
        val groqVerified = AiApiSettingsState(
            hasApiKey = true,
            isConnectionVerified = true,
            selectedModel = "llama-3.3-70b-versatile",
            validationState = ApiKeyValidationState.Valid(4)
        )
        assertEquals(
            "llama-3.3-70b-versatile",
            resolveAuraActiveModelLabel(
                selectedEngine = AiEngineType.GROQ,
                aiSettings = AiApiSettingsState(),
                modelDownloadStatus = ModelDownloadStatus.NotDownloaded,
                llmModelState = LlmModelState.Unloaded,
                activeLocalModelName = "Qwen",
                externalEngineStates = mapOf(AiEngineType.GROQ to groqVerified)
            )
        )

        assertNull(
            resolveAuraActiveModelLabel(
                selectedEngine = AiEngineType.CLAUDE,
                aiSettings = AiApiSettingsState(),
                modelDownloadStatus = ModelDownloadStatus.NotDownloaded,
                llmModelState = LlmModelState.Unloaded,
                activeLocalModelName = "Qwen",
                externalEngineStates = emptyMap()
            )
        )
    }

    @Test
    fun `StorageMetricsCalculator computes library bytes and Drive sync switches`() {
        val book = BookEntity(id = "b1", title = "Física")
        val page = PageEntity(id = "p1", bookId = "b1", title = "Cap 1")
        val note1 = NoteEntity(id = "n1", pageId = "p1", title = "Cinemática", content = "Contenido de prueba para cálculo de bytes.")
        val note2 = NoteEntity(id = "n2", pageId = "p1", title = "Dinámica", content = "F = m * a")

        val metrics = StorageMetricsCalculator.computeMetrics(
            context = context,
            books = listOf(book),
            pages = listOf(page),
            notes = listOf(note1, note2),
            filterState = DriveFilterState(excludedNoteIds = setOf("n2")),
            crashLogsBytes = 128L
        )
        assertEquals(1, metrics.books.size)
        assertEquals(2, metrics.books[0].notes.size)
        assertTrue(metrics.totalLibraryBytes > 0L)
        assertEquals(128L, metrics.totalCrashLogsBytes)
        assertTrue(metrics.books[0].isDriveSyncEnabled)
        assertTrue(metrics.books[0].notes.first { it.note.id == "n1" }.isDriveSyncEnabled)
        assertFalse(metrics.books[0].notes.first { it.note.id == "n2" }.isDriveSyncEnabled)
    }

    @Test
    fun `SettingsHubScreen navigates all 6 categories and switches theme and language live`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = AetherViewModel(app)

        composeTestRule.setContent {
            val appearance by viewModel.appearanceState.collectAsStateWithLifecycle()
            MyApplicationTheme(
                themeId = appearance.themeId,
                colorSchemeMode = appearance.colorSchemeMode,
                pureBlackDarkMode = appearance.pureBlackDarkMode,
                fontScale = appearance.fontScale,
                appLanguage = appearance.appLanguage
            ) {
                SettingsHubScreen(
                    viewModel = viewModel,
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Hub displays Spanish defaults & all 6 categories
        composeTestRule.onNodeWithTag("settings_hub_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ajustes").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_hub_list").performScrollToNode(hasTestTag("settings_category_engine"))
        composeTestRule.onNodeWithTag("settings_category_engine").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_hub_list").performScrollToNode(hasTestTag("settings_category_ai_behavior"))
        composeTestRule.onNodeWithTag("settings_category_ai_behavior").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_hub_list").performScrollToNode(hasTestTag("settings_category_appearance"))
        composeTestRule.onNodeWithTag("settings_category_appearance").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_hub_list").performScrollToNode(hasTestTag("settings_category_language"))
        composeTestRule.onNodeWithTag("settings_category_language").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_hub_list").performScrollToNode(hasTestTag("settings_category_storage_cloud"))
        composeTestRule.onNodeWithTag("settings_category_storage_cloud").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_hub_list").performScrollToNode(hasTestTag("settings_category_advanced"))
        composeTestRule.onNodeWithTag("settings_category_advanced").assertIsDisplayed()

        // 1. Engine settings
        composeTestRule.onNodeWithTag("settings_hub_list").performScrollToNode(hasTestTag("settings_category_engine"))
        composeTestRule.onNodeWithTag("settings_category_engine").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("engine_settings_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("engine_settings_list").performScrollToNode(hasTestTag("engine_groq_card"))
        composeTestRule.onNodeWithTag("engine_groq_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("engine_settings_list").performScrollToNode(hasTestTag("engine_openai_card"))
        composeTestRule.onNodeWithTag("engine_openai_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("engine_settings_list").performScrollToNode(hasTestTag("engine_claude_card"))
        composeTestRule.onNodeWithTag("engine_claude_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("engine_settings_back_button").performClick()
        composeTestRule.waitForIdle()

        // 2. AI Behavior settings
        composeTestRule.onNodeWithTag("settings_hub_list").performScrollToNode(hasTestTag("settings_category_ai_behavior"))
        composeTestRule.onNodeWithTag("settings_category_ai_behavior").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ai_behavior_settings_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_memory_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_behavior_list").performScrollToNode(hasTestTag("ai_about_user_input"))
        composeTestRule.onNodeWithTag("ai_about_user_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_behavior_back_button").performClick()
        composeTestRule.waitForIdle()

        // 3. Appearance settings (15 themes + AMOLED toggle)
        composeTestRule.onNodeWithTag("settings_hub_list").performScrollToNode(hasTestTag("settings_category_appearance"))
        composeTestRule.onNodeWithTag("settings_category_appearance").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("appearance_settings_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("appearance_settings_list").performScrollToNode(hasTestTag("pure_black_switch"))
        composeTestRule.onNodeWithTag("pure_black_switch").performClick()
        composeTestRule.waitForIdle()
        assertTrue(viewModel.appearanceState.value.pureBlackDarkMode)
        composeTestRule.onNodeWithTag("appearance_back_button").performClick()
        composeTestRule.waitForIdle()

        // 4. Storage & Cloud settings
        composeTestRule.onNodeWithTag("settings_hub_list").performScrollToNode(hasTestTag("settings_category_storage_cloud"))
        composeTestRule.onNodeWithTag("settings_category_storage_cloud").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("storage_cloud_settings_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("storage_total_size_label").assertIsDisplayed()
        composeTestRule.onNodeWithTag("storage_back_button").performClick()
        composeTestRule.waitForIdle()

        // 5. Advanced & Crash Reports
        composeTestRule.onNodeWithTag("settings_hub_list").performScrollToNode(hasTestTag("settings_category_advanced"))
        composeTestRule.onNodeWithTag("settings_category_advanced").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("advanced_settings_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("crash_diagnostic_trigger_btn").performClick()
        composeTestRule.waitForIdle()
        assertTrue(viewModel.crashReports.value.isNotEmpty())
        composeTestRule.onNodeWithTag("crash_clear_all_btn").performClick()
        composeTestRule.waitForIdle()
        assertTrue(viewModel.crashReports.value.isEmpty())
        composeTestRule.onNodeWithTag("advanced_back_button").performClick()
        composeTestRule.waitForIdle()

        // 6. Language settings: switch to English and verify live translation on Hub
        composeTestRule.onNodeWithTag("settings_hub_list").performScrollToNode(hasTestTag("settings_category_language"))
        composeTestRule.onNodeWithTag("settings_category_language").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("language_settings_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("language_option_en").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("App Language").assertIsDisplayed()
        composeTestRule.onNodeWithTag("language_back_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_hub_list").performScrollToNode(hasTestTag("settings_category_engine"))
        composeTestRule.onNodeWithText("AI Engines").assertIsDisplayed()
    }
}
