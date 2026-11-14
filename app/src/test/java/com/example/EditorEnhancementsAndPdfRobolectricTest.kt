package com.example

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.ai.AiApiSettingsState
import com.example.data.local.ai.ApiKeyValidationState
import com.example.data.local.ai.AiStudioModelInfo
import com.example.data.model.NoteEntity
import com.example.data.remote.DriveSyncManager
import com.example.data.remote.ai.ExternalAiProvider
import com.example.ui.editor.findInNoteSearchMatches
import com.example.ui.editor.shouldStartNewTypingGroup
import com.example.ui.editor.splitParagraphTextAtCursor
import com.example.ui.pdf.PdfBannerContentMode
import com.example.ui.pdf.PdfConfigStore
import com.example.ui.pdf.PdfExportConfig
import com.example.ui.pdf.PdfExportManager
import com.example.ui.pdf.PdfMarginPreset
import com.example.ui.pdf.PdfPageNumberPosition
import com.example.ui.pdf.PdfPageNumberStyle
import com.example.ui.pdf.PdfPreviewScreen
import com.example.ui.settings.ExternalProviderEngineCard
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EditorEnhancementsAndPdfRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `splitParagraphTextAtCursor splits cleanly at cursor respecting sentinel offset`() {
        val mid = splitParagraphTextAtCursor(
            content = "Hola Mundo",
            rawSelectionStart = 6, // "\u200BHola |Mundo" -> offset 5 in content
            hasLeadingSentinel = true
        )
        assertEquals("Hola ", mid.beforeContent)
        assertEquals("Mundo", mid.afterContent)

        val start = splitParagraphTextAtCursor(
            content = "Texto completo",
            rawSelectionStart = 0, // before sentinel clamps to 0
            hasLeadingSentinel = true
        )
        assertEquals("", start.beforeContent)
        assertEquals("Texto completo", start.afterContent)

        val end = splitParagraphTextAtCursor(
            content = "Final",
            rawSelectionStart = 99,
            hasLeadingSentinel = false
        )
        assertEquals("Final", end.beforeContent)
        assertEquals("", end.afterContent)
    }

    @Test
    fun `shouldStartNewTypingGroup groups words and breaks on pause, word boundary, direction switch, or field switch`() {
        // Initial edit starts a group
        assertTrue(shouldStartNewTypingGroup(null, 0L, 0, "b1", 1000L, "", "H"))

        // Typing consecutive characters within the same word (<850ms) stays in same group
        assertFalse(shouldStartNewTypingGroup("b1", 1000L, 1, "b1", 1120L, "Hol", "Hola"))

        // Starting a new word after trailing space starts a new group
        assertTrue(shouldStartNewTypingGroup("b1", 1200L, 1, "b1", 1300L, "Hola ", "Hola M"))

        // Pause longer than 850ms starts a new group
        assertTrue(shouldStartNewTypingGroup("b1", 1000L, 1, "b1", 2000L, "Hola", "Hola!"))

        // Direction change (typing -> deleting) starts a new group
        assertTrue(shouldStartNewTypingGroup("b1", 1000L, 1, "b1", 1100L, "Hola", "Hol"))

        // Multi-character paste starts a new group
        assertTrue(shouldStartNewTypingGroup("b1", 1000L, 1, "b1", 1100L, "Hola", "Hola pegado"))

        // Switching blocks starts a new group
        assertTrue(shouldStartNewTypingGroup("b1", 1000L, 1, "b2", 1100L, "x", "xy"))
    }

    @Test
    fun `findInNoteSearchMatches locates every exact phrase match across all blocks`() {
        val blocks = listOf(
            EditorBlock.Text(id = "t1", content = "Esta nota tiene una NOTA doble"),
            EditorBlock.Todo(id = "td1", content = "Revisar nota urgente", isChecked = false),
            EditorBlock.Quote(id = "q1", content = "Cita sin coincidencia"),
            EditorBlock.Callout(id = "c1", content = "Nota final importante")
        )
        val matches = findInNoteSearchMatches(blocks, "nota")
        assertEquals(4, matches.size)
        assertEquals("t1", matches[0].blockId)
        assertEquals(5, matches[0].startInContent)
        assertEquals(9, matches[0].endInContent)
        assertEquals("t1", matches[1].blockId)
        assertEquals(20, matches[1].startInContent)
        assertEquals(24, matches[1].endInContent)
        assertEquals("td1", matches[2].blockId)
        assertFalse(matches[2].isEditableTextBlock)
        assertEquals("c1", matches[3].blockId)
    }

    @Test
    fun `PdfExportManager formats Roman numerals and PdfConfigStore persists 4 custom margins`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("I", PdfExportManager.toRomanNumeral(1))
        assertEquals("IV", PdfExportManager.toRomanNumeral(4))
        assertEquals("IX", PdfExportManager.toRomanNumeral(9))
        assertEquals("XIV", PdfExportManager.toRomanNumeral(14))

        val store = PdfConfigStore(context)
        val customConfig = PdfExportConfig(
            showPageNumbers = true,
            pageNumberStyle = PdfPageNumberStyle.ROMAN,
            pageNumberFontSize = 13f,
            pageNumberFontFamily = "Montserrat",
            pageNumberPosition = PdfPageNumberPosition.CENTER_RIGHT,
            headerEnabled = true,
            headerHeightPt = 34f,
            headerContentMode = PdfBannerContentMode.CUSTOM_TEXT,
            headerCustomText = "Encabezado Custom",
            marginPreset = PdfMarginPreset.CUSTOM,
            customMarginTop = 30f,
            customMarginBottom = 42f,
            customMarginLeft = 24f,
            customMarginRight = 64f
        )
        store.save(customConfig)

        val reloaded = store.load()
        assertEquals(PdfPageNumberStyle.ROMAN, reloaded.pageNumberStyle)
        assertEquals(PdfPageNumberPosition.CENTER_RIGHT, reloaded.pageNumberPosition)
        assertEquals("Montserrat", reloaded.pageNumberFontFamily)
        assertEquals("Encabezado Custom", reloaded.headerCustomText)
        assertEquals(PdfMarginPreset.CUSTOM, reloaded.marginPreset)
        val margins = reloaded.resolveMargins()
        assertEquals(30f, margins.top, 0.01f)
        assertEquals(42f, margins.bottom, 0.01f)
        assertEquals(24f, margins.left, 0.01f)
        assertEquals(64f, margins.right, 0.01f)
    }

    @Test
    fun `External providers hide model picker until API validation succeeds`() {
        var groqState by mutableStateOf(
            AiApiSettingsState(
                hasApiKey = true,
                isConnectionVerified = false,
                selectedModel = "llama-3.3-70b-versatile",
                discoveredModels = listOf(AiStudioModelInfo("llama-3.3-70b-versatile", "Llama 3.3 70B")),
                validationState = ApiKeyValidationState.Idle
            )
        )
        var openAiState by mutableStateOf(
            AiApiSettingsState(
                hasApiKey = true,
                isConnectionVerified = false,
                selectedModel = "gpt-4o",
                discoveredModels = listOf(AiStudioModelInfo("gpt-4o", "GPT-4o")),
                validationState = ApiKeyValidationState.Invalid("HTTP 401")
            )
        )
        var claudeState by mutableStateOf(
            AiApiSettingsState(
                hasApiKey = false,
                isConnectionVerified = false,
                selectedModel = "claude-3-5-sonnet-latest",
                discoveredModels = emptyList(),
                validationState = ApiKeyValidationState.Idle
            )
        )

        var activeProvider by mutableStateOf(ExternalAiProvider.GROQ)
        var selectedGroqModel by mutableStateOf("")

        composeTestRule.setContent {
            MyApplicationTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    ExternalProviderEngineCard(
                        provider = ExternalAiProvider.GROQ,
                        isActive = activeProvider == ExternalAiProvider.GROQ,
                        state = groqState,
                        onSelectEngine = { activeProvider = ExternalAiProvider.GROQ },
                        onSaveApiKey = {},
                        onClearApiKey = {},
                        onTestConnection = {},
                        onSelectModel = { selectedGroqModel = it }
                    )
                    ExternalProviderEngineCard(
                        provider = ExternalAiProvider.OPENAI,
                        isActive = activeProvider == ExternalAiProvider.OPENAI,
                        state = openAiState,
                        onSelectEngine = { activeProvider = ExternalAiProvider.OPENAI },
                        onSaveApiKey = {},
                        onClearApiKey = {},
                        onTestConnection = {},
                        onSelectModel = {}
                    )
                    ExternalProviderEngineCard(
                        provider = ExternalAiProvider.CLAUDE,
                        isActive = activeProvider == ExternalAiProvider.CLAUDE,
                        state = claudeState,
                        onSelectEngine = { activeProvider = ExternalAiProvider.CLAUDE },
                        onSaveApiKey = {},
                        onClearApiKey = {},
                        onTestConnection = {},
                        onSelectModel = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // Must stay hidden when validationState is Idle / Invalid
        composeTestRule.onNodeWithTag("engine_groq_model_selector_section", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.runOnUiThread { activeProvider = ExternalAiProvider.OPENAI }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("engine_openai_model_selector_section", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.runOnUiThread { activeProvider = ExternalAiProvider.CLAUDE }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("engine_claude_model_selector_section", useUnmergedTree = true).assertDoesNotExist()

        composeTestRule.runOnUiThread {
            groqState = groqState.copy(
                isConnectionVerified = true,
                discoveredModels = listOf(
                    AiStudioModelInfo("llama-3.3-70b-versatile", "Llama 3.3 70B"),
                    AiStudioModelInfo("mixtral-8x7b-32768", "Mixtral 8x7B")
                ),
                validationState = ApiKeyValidationState.Valid(2)
            )
            openAiState = openAiState.copy(
                isConnectionVerified = true,
                discoveredModels = listOf(
                    AiStudioModelInfo("gpt-4o", "GPT-4o"),
                    AiStudioModelInfo("gpt-4o-mini", "GPT-4o mini")
                ),
                validationState = ApiKeyValidationState.Valid(2)
            )
            claudeState = claudeState.copy(
                hasApiKey = true,
                isConnectionVerified = true,
                discoveredModels = listOf(
                    AiStudioModelInfo("claude-3-5-sonnet-latest", "Claude 3.5 Sonnet")
                ),
                validationState = ApiKeyValidationState.Valid(1)
            )
            activeProvider = ExternalAiProvider.GROQ
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("engine_groq_model_selector_section", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("engine_groq_model_picker", useUnmergedTree = true).performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("engine_groq_model_option_mixtral-8x7b-32768", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals("mixtral-8x7b-32768", selectedGroqModel)

        composeTestRule.runOnUiThread { activeProvider = ExternalAiProvider.OPENAI }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("engine_openai_model_selector_section", useUnmergedTree = true).performScrollTo().assertIsDisplayed()

        composeTestRule.runOnUiThread { activeProvider = ExternalAiProvider.CLAUDE }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("engine_claude_model_selector_section", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `NoteEditorWorkspace splits paragraph at cursor, undoes and redoes formatting and splits, and auto-saves after 2s`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val syncManager = DriveSyncManager(context, db)

        var latestSavedNote = NoteEntity(
            id = "n_split_undo",
            pageId = "pg_1",
            title = "Prueba Split",
            content = serializeBlocks(listOf(EditorBlock.Text(id = "b_0", content = "Hola Mundo")))
        )
        var saveCount = 0

        try {
            composeTestRule.setContent {
                MyApplicationTheme {
                    NoteEditorWorkspace(
                        note = latestSavedNote,
                        aiLoading = false,
                        onDismiss = {},
                        onSave = {
                            latestSavedNote = it
                            saveCount++
                        },
                        onAiModify = {},
                        onOpenChatbot = {},
                        syncManager = syncManager
                    )
                }
            }
            composeTestRule.waitForIdle()

            // Focus text block 0 and place cursor between "Hola " and "Mundo" (+1 for sentinel '\u200B')
            composeTestRule.onNodeWithTag("text_block_0").performClick()
            composeTestRule.onNodeWithTag("text_block_0").performTextInputSelection(TextRange(6))
            composeTestRule.waitForIdle()

            // Tap "↪" split paragraph button placed between ∓ and Delete
            composeTestRule.onNodeWithTag("editor_main_toolbar_lazy_row")
                .performScrollToNode(hasTestTag("editor_split_paragraph_btn"))
            composeTestRule.onNodeWithTag("editor_split_paragraph_btn").performClick()
            composeTestRule.waitForIdle()

            val afterSplit = parseBlocks(latestSavedNote.content)
            assertEquals(2, afterSplit.size)
            assertEquals("Hola ", (afterSplit[0] as EditorBlock.Text).content)
            assertEquals("Mundo", (afterSplit[1] as EditorBlock.Text).content)

            // Scroll LazyRow back to Formato (Aa), open it, apply Justify alignment on split second block, and undo + redo
            composeTestRule.onNodeWithTag("editor_main_toolbar_lazy_row")
                .performScrollToNode(hasContentDescription("Formato"))
            composeTestRule.onNodeWithContentDescription("Formato").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("editor_formatting_lazy_row")
                .performScrollToNode(hasTestTag("editor_align_justify_btn"))
            composeTestRule.onNodeWithTag("editor_align_justify_btn").performClick()
            composeTestRule.waitForIdle()
            assertEquals("Justify", (parseBlocks(latestSavedNote.content)[1] as EditorBlock.Text).alignment)

            // Undo alignment -> Undo split -> Redo split
            composeTestRule.onNodeWithTag("editor_main_toolbar_lazy_row")
                .performScrollToNode(hasTestTag("editor_undo_btn"))
            composeTestRule.onNodeWithTag("editor_undo_btn").performClick()
            composeTestRule.waitForIdle()
            assertEquals("Left", (parseBlocks(latestSavedNote.content)[1] as EditorBlock.Text).alignment)

            composeTestRule.onNodeWithTag("editor_undo_btn").performClick()
            composeTestRule.waitForIdle()
            val afterUndoSplit = parseBlocks(latestSavedNote.content)
            assertEquals(1, afterUndoSplit.size)
            assertEquals("Hola Mundo", (afterUndoSplit[0] as EditorBlock.Text).content)

            composeTestRule.onNodeWithTag("editor_redo_btn").performClick()
            composeTestRule.waitForIdle()
            val afterRedoSplit = parseBlocks(latestSavedNote.content)
            assertEquals(2, afterRedoSplit.size)
            assertEquals("Mundo", (afterRedoSplit[1] as EditorBlock.Text).content)

            // Type inside paragraph 1 and confirm 2-second auto-save persists changes automatically
            val savesBeforeTyping = saveCount
            composeTestRule.onNodeWithTag("text_block_1").performClick()
            composeTestRule.onNodeWithTag("text_block_1").performTextInput(" Nuevo")
            composeTestRule.mainClock.advanceTimeBy(2200)
            composeTestRule.waitForIdle()

            assertTrue(saveCount > savesBeforeTyping)
            assertTrue(
                parseBlocks(latestSavedNote.content)
                    .filterIsInstance<EditorBlock.Text>()
                    .any { it.content.contains("Nuevo") }
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun `In-note search shows exact match counter and navigates up and down across paragraphs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val syncManager = DriveSyncManager(context, db)
        val note = NoteEntity(
            id = "n_search_nav",
            pageId = "pg_1",
            title = "Búsqueda",
            content = serializeBlocks(
                listOf(
                    EditorBlock.Text(id = "s0", content = "Primera nota con nota"),
                    EditorBlock.Text(id = "s1", content = "Segunda nota final")
                )
            )
        )

        try {
            composeTestRule.setContent {
                MyApplicationTheme {
                    NoteEditorWorkspace(
                        note = note,
                        aiLoading = false,
                        onDismiss = {},
                        onSave = {},
                        onAiModify = {},
                        onOpenChatbot = {},
                        syncManager = syncManager
                    )
                }
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("open_in_note_search_btn").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("in_note_search_counter").assertTextEquals("0/0")
            composeTestRule.onNodeWithTag("in_note_search_input").performTextInput("nota")
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("in_note_search_counter").assertTextEquals("1/3")
            composeTestRule.onNodeWithTag("in_note_search_next_btn").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("in_note_search_counter").assertTextEquals("2/3")

            composeTestRule.onNodeWithTag("in_note_search_next_btn").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("in_note_search_counter").assertTextEquals("3/3")

            composeTestRule.onNodeWithTag("in_note_search_next_btn").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("in_note_search_counter").assertTextEquals("1/3")

            composeTestRule.onNodeWithTag("in_note_search_prev_btn").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("in_note_search_counter").assertTextEquals("3/3")
        } finally {
            db.close()
        }
    }

    @Test
    fun `Note menu opens Vista de PDF configurator and updates Roman numbering, header, and custom margins`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val syncManager = DriveSyncManager(context, db)
        val note = NoteEntity(
            id = "n_pdf_preview",
            pageId = "pg_1",
            title = "Informe Final",
            content = serializeBlocks(listOf(EditorBlock.Text(id = "p1", content = "Contenido del PDF")))
        )

        try {
            composeTestRule.setContent {
                MyApplicationTheme {
                    NoteEditorWorkspace(
                        note = note,
                        aiLoading = false,
                        onDismiss = {},
                        onSave = {},
                        onAiModify = {},
                        onOpenChatbot = {},
                        syncManager = syncManager
                    )
                }
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("editor_more_menu_btn").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("menu_pdf_preview_item").assertIsDisplayed()
            composeTestRule.onNodeWithTag("menu_export_pdf_item").assertIsDisplayed()

            composeTestRule.onNodeWithTag("menu_pdf_preview_item").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("pdf_preview_screen").assertIsDisplayed()
            composeTestRule.onNodeWithTag("pdf_number_style_roman").performClick()
            composeTestRule.waitForIdle()
            assertEquals("I", PdfConfigStore(context).load().let {
                PdfExportManager.formatPageNumber(1, it.pageNumberStyle)
            })

            composeTestRule.onNodeWithTag("pdf_tab_header_footer").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("pdf_header_switch").performClick()
            composeTestRule.waitForIdle()
            assertTrue(PdfConfigStore(context).load().headerEnabled)

            composeTestRule.onNodeWithTag("pdf_tab_margins").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("pdf_margin_preset_custom").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("pdf_margin_slider_top").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("pdf_margin_slider_bottom").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("pdf_margin_slider_left").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("pdf_margin_slider_right").performScrollTo().assertIsDisplayed()
        } finally {
            db.close()
        }
    }
}
