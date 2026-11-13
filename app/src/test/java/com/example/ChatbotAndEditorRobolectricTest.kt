package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.ai.AiApiSettingsState
import com.example.data.local.ai.ApiKeyValidationState
import com.example.data.local.llm.LlmModelState
import com.example.data.local.llm.ModelDownloadStatus
import com.example.data.model.BookEntity
import com.example.data.model.NoteEntity
import com.example.data.model.PageEntity
import com.example.data.remote.DriveSyncManager
import com.example.ui.ActiveChatToken
import com.example.ui.AiEngineType
import com.example.ui.AuraHeaderTitlePill
import com.example.ui.AuraInputWithInlineBadges
import com.example.ui.ChatShortcutKind
import com.example.ui.ChatbotShortcutPopup
import com.example.ui.UserMessageBubbleRow
import com.example.ui.buildShortcutSuggestions
import com.example.ui.detectActiveTrigger
import com.example.ui.insertShortcutToken
import com.example.ui.reconcileTokensOnTextChange
import com.example.ui.removeAssistantTurnResponses
import com.example.ui.resolveAuraActiveModelLabel
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChatbotAndEditorRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `detectActiveTrigger handles slash bang and at triggers cleanly`() {
        assertEquals('/', detectActiveTrigger("/bio", 4, emptyList())?.triggerChar)
        assertEquals("bio", detectActiveTrigger("/bio", 4, emptyList())?.query)

        assertEquals('!', detectActiveTrigger("Hola !tra", 9, emptyList())?.triggerChar)
        assertEquals("tra", detectActiveTrigger("Hola !tra", 9, emptyList())?.query)

        assertEquals('@', detectActiveTrigger("Revisa @Tab", 11, emptyList())?.triggerChar)
        assertEquals("Tab", detectActiveTrigger("Revisa @Tab", 11, emptyList())?.query)

        // Mid-word triggers should NOT trigger shortcut menu
        assertNull(detectActiveTrigger("correo@dominio.com", 18, emptyList()))
        assertNull(detectActiveTrigger("http://ejemplo", 14, emptyList()))

        // Text inside an existing inline token must not re-trigger
        val existingToken = ActiveChatToken(
            id = "tok_1",
            triggerChar = '@',
            label = "Párrafo 1",
            kind = ChatShortcutKind.PARAGRAPH,
            targetId = "p1",
            resolvedContent = "Texto",
            start = 0,
            end = 9
        )
        assertNull(detectActiveTrigger("Párrafo 1", 5, listOf(existingToken)))
    }

    @Test
    fun `insertShortcutToken removes symbol and tracks inline token range with atomic backspace`() {
        val initialText = "Analiza @par"
        val initialTfv = TextFieldValue(initialText, TextRange(initialText.length))
        val trigger = detectActiveTrigger(initialTfv.text, initialTfv.selection.start, emptyList())!!
        val option = com.example.ui.ChatShortcutOption(
            id = "para_1",
            triggerChar = '@',
            label = "Párrafo 1",
            subtitle = "Contenido inicial",
            kind = ChatShortcutKind.PARAGRAPH,
            targetId = "b1",
            resolvedContent = "Contenido inicial del párrafo"
        )

        val (afterInsertTfv, tokens) = insertShortcutToken(initialTfv, emptyList(), trigger, option)
        assertEquals("Analiza Párrafo 1 ", afterInsertTfv.text)
        assertFalse(afterInsertTfv.text.contains("@"))
        assertEquals(1, tokens.size)
        assertEquals(8, tokens[0].start)
        assertEquals(17, tokens[0].end)

        // Simulated backspace inside the token removes the entire gray badge atomically
        val edited = TextFieldValue(
            text = afterInsertTfv.text.removeRange(15, 16),
            selection = TextRange(15)
        )
        val (reconciledTfv, reconciledTokens) = reconcileTokensOnTextChange(afterInsertTfv, edited, tokens)
        assertEquals("Analiza ", reconciledTfv.text)
        assertTrue(reconciledTokens.isEmpty())
    }

    @Test
    fun `buildShortcutSuggestions includes outside and inside routes for slash, functions for bang, and paragraphs plus graphics for at`() {
        val bookCurrent = BookEntity(id = "b_curr", title = "Física")
        val bookOutside = BookEntity(id = "b_out", title = "Historia")
        val pageCurr = PageEntity(id = "pg_curr", bookId = "b_curr", title = "Tema 1")
        val pageOut = PageEntity(id = "pg_out", bookId = "b_out", title = "Tema 2")

        val activeNoteContent = serializeBlocks(
            listOf(
                EditorBlock.Text(id = "p1", content = "Primer párrafo sobre energía cinética"),
                EditorBlock.Table(id = "tbl1", rows = 2, cols = 2, data = listOf(listOf("Masa", "Velocidad"), listOf("2kg", "5m/s"))),
                EditorBlock.Callout(id = "cal1", content = "Recordar unidades SI", emoji = "💡")
            )
        )
        val activeNote = NoteEntity(id = "n_curr", pageId = "pg_curr", title = "Apuntes Energía", content = activeNoteContent)
        val outsideNote = NoteEntity(id = "n_out", pageId = "pg_out", title = "Revolución Industrial", content = "Texto histórico")

        val slashResults = buildShortcutSuggestions(
            triggerChar = '/',
            query = "",
            allNotes = listOf(activeNote, outsideNote),
            allBooks = listOf(bookCurrent, bookOutside),
            allPages = listOf(pageCurr, pageOut),
            activeNote = activeNote,
            activeBook = bookCurrent
        )
        assertTrue(slashResults.any { it.kind == ChatShortcutKind.NOTE && it.targetId == "n_out" })
        assertTrue(slashResults.any { it.kind == ChatShortcutKind.NOTE && it.targetId == "n_curr" })
        assertTrue(slashResults.any { it.kind == ChatShortcutKind.BOOK && it.targetId == "b_out" })
        assertTrue(slashResults.any { it.kind == ChatShortcutKind.BOOK && it.targetId == "b_curr" })

        val bangResults = buildShortcutSuggestions(
            triggerChar = '!',
            query = "",
            allNotes = listOf(activeNote, outsideNote),
            allBooks = listOf(bookCurrent, bookOutside),
            allPages = listOf(pageCurr, pageOut),
            activeNote = activeNote,
            activeBook = bookCurrent
        )
        assertTrue(bangResults.any { it.label == "Agregar elemento gráfico" })
        assertTrue(bangResults.any { it.label == "Traducir" })
        assertTrue(bangResults.any { it.label == "Modificar nota" })

        val atResults = buildShortcutSuggestions(
            triggerChar = '@',
            query = "",
            allNotes = listOf(activeNote, outsideNote),
            allBooks = listOf(bookCurrent, bookOutside),
            allPages = listOf(pageCurr, pageOut),
            activeNote = activeNote,
            activeBook = bookCurrent
        )
        assertTrue(atResults.any { it.kind == ChatShortcutKind.PARAGRAPH && it.label == "Párrafo 1" })
        assertTrue(atResults.any { it.kind == ChatShortcutKind.GRAPHIC_ELEMENT && it.label.startsWith("Tabla") })
        assertTrue(atResults.any { it.kind == ChatShortcutKind.GRAPHIC_ELEMENT && it.label.startsWith("Destacado") })
        assertTrue(atResults.any { it.kind == ChatShortcutKind.NOTE && it.targetId == "n_out" })
    }

    @Test
    fun `resolveAuraActiveModelLabel hides model when none configured and returns model name when active`() {
        val noCloud = resolveAuraActiveModelLabel(
            selectedEngine = AiEngineType.GOOGLE_AI_STUDIO,
            aiSettings = AiApiSettingsState(hasApiKey = false, isConnectionVerified = false),
            modelDownloadStatus = ModelDownloadStatus.NotDownloaded,
            llmModelState = LlmModelState.Unloaded,
            activeLocalModelName = "Qwen 2.5 1.5B"
        )
        assertNull(noCloud)

        val unverifiedCloud = resolveAuraActiveModelLabel(
            selectedEngine = AiEngineType.GOOGLE_AI_STUDIO,
            aiSettings = AiApiSettingsState(
                hasApiKey = true,
                isConnectionVerified = false,
                selectedModel = "gemini-2.5-flash",
                validationState = ApiKeyValidationState.Idle
            ),
            modelDownloadStatus = ModelDownloadStatus.NotDownloaded,
            llmModelState = LlmModelState.Unloaded,
            activeLocalModelName = "Qwen 2.5 1.5B"
        )
        assertNull(unverifiedCloud)

        val verifiedCloud = resolveAuraActiveModelLabel(
            selectedEngine = AiEngineType.GOOGLE_AI_STUDIO,
            aiSettings = AiApiSettingsState(
                hasApiKey = true,
                isConnectionVerified = true,
                selectedModel = "gemini-2.5-pro",
                validationState = ApiKeyValidationState.Valid(2)
            ),
            modelDownloadStatus = ModelDownloadStatus.NotDownloaded,
            llmModelState = LlmModelState.Unloaded,
            activeLocalModelName = "Qwen 2.5 1.5B"
        )
        assertEquals("gemini-2.5-pro", verifiedCloud)

        val noLocal = resolveAuraActiveModelLabel(
            selectedEngine = AiEngineType.AURA_LOCAL,
            aiSettings = AiApiSettingsState(),
            modelDownloadStatus = ModelDownloadStatus.NotDownloaded,
            llmModelState = LlmModelState.Unloaded,
            activeLocalModelName = "Qwen 2.5 1.5B"
        )
        assertNull(noLocal)

        val readyLocal = resolveAuraActiveModelLabel(
            selectedEngine = AiEngineType.AURA_LOCAL,
            aiSettings = AiApiSettingsState(),
            modelDownloadStatus = ModelDownloadStatus.Ready(File("model.gguf"), 900.0),
            llmModelState = LlmModelState.Unloaded,
            activeLocalModelName = "Qwen 2.5 1.5B"
        )
        assertEquals("Qwen 2.5 1.5B", readyLocal)
    }

    @Test
    fun `removeAssistantTurnResponses deletes prior assistant reply cleanly without duplicating`() {
        val conversation = listOf(
            "Hola Aura" to true,
            "¡Hola! ¿En qué te ayudo?" to false,
            "Resume esto" to true,
            "Respuesta antigua 1" to false,
            "Respuesta duplicada extra" to false,
            "Siguiente pregunta" to true,
            "Respuesta siguiente" to false
        )

        val (cleaned, insertSlot) = removeAssistantTurnResponses(conversation, userIndex = 2)
        assertEquals(3, insertSlot)
        assertEquals(
            listOf(
                "Hola Aura" to true,
                "¡Hola! ¿En qué te ayudo?" to false,
                "Resume esto" to true,
                "Siguiente pregunta" to true,
                "Respuesta siguiente" to false
            ),
            cleaned
        )
    }

    @Test
    fun `serializeBlocks and parseBlocks persist Justify alignment`() {
        val blocks = listOf(
            EditorBlock.Text(id = "j1", content = "Párrafo justificado", alignment = "Justify"),
            EditorBlock.Text(id = "c1", content = "Párrafo centrado", alignment = "Center")
        )
        val serialized = serializeBlocks(blocks)
        val restored = parseBlocks(serialized)
        assertEquals(2, restored.size)
        assertEquals("Justify", (restored[0] as EditorBlock.Text).alignment)
        assertEquals("Center", (restored[1] as EditorBlock.Text).alignment)
    }

    @Test
    fun `AuraHeaderTitlePill hides badge when activeModelLabel is null and shows model when present`() {
        var modelLabel by mutableStateOf<String?>(null)

        composeTestRule.setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    AuraHeaderTitlePill(
                        selectedEngine = AiEngineType.GOOGLE_AI_STUDIO,
                        activeModelLabel = modelLabel,
                        onClick = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Aura IA").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chatbot_active_model_badge").assertDoesNotExist()

        composeTestRule.runOnUiThread {
            modelLabel = "gemini-2.5-flash"
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("chatbot_active_model_badge", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("gemini-2.5-flash", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `ChatbotShortcutPopup selects option and inserts symbol-free badge in AuraInputWithInlineBadges`() {
        val options = listOf(
            com.example.ui.ChatShortcutOption(
                id = "fn_translate",
                triggerChar = '!',
                label = "Traducir",
                subtitle = "Traduce la nota o el texto seleccionado",
                kind = ChatShortcutKind.FUNCTION,
                targetId = "translate",
                resolvedContent = "Traduce el contenido"
            )
        )
        var capturedText = ""
        var capturedTokens: List<ActiveChatToken> = emptyList()

        composeTestRule.setContent {
            MyApplicationTheme {
                var tfv by remember { mutableStateOf(TextFieldValue("!tra", TextRange(4))) }
                var tokens by remember { mutableStateOf<List<ActiveChatToken>>(emptyList()) }
                val trigger = detectActiveTrigger(tfv.text, tfv.selection.start, tokens)

                Column(modifier = Modifier.fillMaxWidth()) {
                    ChatbotShortcutPopup(
                        visible = trigger != null,
                        triggerChar = trigger?.triggerChar,
                        suggestions = options,
                        onSelect = { opt ->
                            val t = trigger!!
                            val (nextTfv, nextTokens) = insertShortcutToken(tfv, tokens, t, opt)
                            tfv = nextTfv
                            tokens = nextTokens
                            capturedText = nextTfv.text
                            capturedTokens = nextTokens
                        }
                    )
                    AuraInputWithInlineBadges(
                        value = tfv,
                        activeTokens = tokens,
                        onValueChange = { tfv = it },
                        placeholderText = "Escribe a Aura..."
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("chatbot_shortcut_menu").assertIsDisplayed()
        composeTestRule.onNodeWithTag("shortcut_item_fn_translate").performClick()
        composeTestRule.waitForIdle()

        assertEquals("Traducir ", capturedText)
        assertFalse(capturedText.contains("!"))
        assertEquals(1, capturedTokens.size)
        assertEquals("Traducir", capturedTokens.first().label)
        composeTestRule.onNodeWithTag("chatbot_shortcut_menu").assertDoesNotExist()
    }

    @Test
    fun `UserMessageBubbleRow opens left Editar and Copiar menu on long press`() {
        var editedClicked = false
        var copiedClicked = false

        composeTestRule.setContent {
            MyApplicationTheme {
                var openMenu by remember { mutableStateOf(false) }
                UserMessageBubbleRow(
                    index = 0,
                    annotatedText = AnnotatedString("Explica la fotosíntesis"),
                    isMenuOpen = openMenu,
                    onOpenMenu = { openMenu = true },
                    onDismissMenu = { openMenu = false },
                    onEditClick = { editedClicked = true },
                    onCopyClick = { copiedClicked = true },
                    onNoteAnnotationClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("user_msg_context_menu").assertDoesNotExist()

        composeTestRule.onNodeWithTag("user_message_bubble_0").performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("user_msg_context_menu").assertIsDisplayed()
        composeTestRule.onNodeWithTag("user_msg_edit_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("user_msg_copy_btn").assertIsDisplayed()

        composeTestRule.onNodeWithTag("user_msg_edit_btn").performClick()
        composeTestRule.waitForIdle()
        assertTrue(editedClicked)

        // Re-open to verify Copiar as well
        composeTestRule.onNodeWithTag("user_message_bubble_0").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("user_msg_copy_btn").performClick()
        composeTestRule.waitForIdle()
        assertTrue(copiedClicked)
    }

    @Test
    fun `NoteEditorWorkspace supports plus-minus paragraphs, sequential graphic insertion, and 4 alignment icon buttons`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val syncManager = DriveSyncManager(context, db)
        val initialBlocks = listOf(
            EditorBlock.Text(id = "p0", content = "Primer párrafo"),
            EditorBlock.Text(id = "p1", content = "Segundo párrafo"),
            EditorBlock.Text(id = "p2", content = "Tercer párrafo")
        )
        var latestSavedNote = NoteEntity(
            id = "n_editor",
            pageId = "pg_1",
            title = "Nota de prueba",
            content = serializeBlocks(initialBlocks)
        )

        try {
            composeTestRule.setContent {
                MyApplicationTheme {
                    NoteEditorWorkspace(
                        note = latestSavedNote,
                        aiLoading = false,
                        onDismiss = {},
                        onSave = { latestSavedNote = it },
                        onAiModify = {},
                        onOpenChatbot = {},
                        syncManager = syncManager
                    )
                }
            }
            composeTestRule.waitForIdle()

            // Select middle paragraph (index 1) and open the Aa formatting panel
            composeTestRule.onNodeWithTag("text_block_1").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithContentDescription("Formato").performClick()
            composeTestRule.waitForIdle()

            // Verify 4 direct icon-only alignment buttons in Aa LazyRow
            composeTestRule.onNodeWithTag("editor_formatting_lazy_row")
                .performScrollToNode(hasTestTag("editor_align_left_btn"))
            composeTestRule.onNodeWithTag("editor_align_left_btn").assertIsDisplayed()
            composeTestRule.onNodeWithTag("editor_formatting_lazy_row")
                .performScrollToNode(hasTestTag("editor_align_center_btn"))
            composeTestRule.onNodeWithTag("editor_align_center_btn").assertIsDisplayed()
            composeTestRule.onNodeWithTag("editor_formatting_lazy_row")
                .performScrollToNode(hasTestTag("editor_align_right_btn"))
            composeTestRule.onNodeWithTag("editor_align_right_btn").assertIsDisplayed()
            composeTestRule.onNodeWithTag("editor_formatting_lazy_row")
                .performScrollToNode(hasTestTag("editor_align_justify_btn"))
            composeTestRule.onNodeWithTag("editor_align_justify_btn").assertIsDisplayed()

            composeTestRule.onNodeWithTag("editor_align_justify_btn").performClick()
            composeTestRule.waitForIdle()
            val afterAlign = parseBlocks(latestSavedNote.content)
            assertEquals("Justify", (afterAlign[1] as EditorBlock.Text).alignment)

            // Test ± (insert paragraph above current index 1)
            composeTestRule.onNodeWithTag("editor_main_toolbar_lazy_row")
                .performScrollToNode(hasTestTag("editor_insert_para_above_btn"))
            composeTestRule.onNodeWithTag("editor_insert_para_above_btn").performClick()
            composeTestRule.waitForIdle()
            val afterAbove = parseBlocks(latestSavedNote.content)
            assertEquals(4, afterAbove.size)
            assertEquals("Primer párrafo", (afterAbove[0] as EditorBlock.Text).content)
            assertEquals("", (afterAbove[1] as EditorBlock.Text).content)
            assertEquals("Segundo párrafo", (afterAbove[2] as EditorBlock.Text).content)

            // Now selectedBlockIndex is 1 (the newly inserted blank paragraph).
            // Test ∓ (insert paragraph below current index 1)
            composeTestRule.onNodeWithTag("editor_main_toolbar_lazy_row")
                .performScrollToNode(hasTestTag("editor_insert_para_below_btn"))
            composeTestRule.onNodeWithTag("editor_insert_para_below_btn").performClick()
            composeTestRule.waitForIdle()
            val afterBelow = parseBlocks(latestSavedNote.content)
            assertEquals(5, afterBelow.size)
            assertEquals("", (afterBelow[2] as EditorBlock.Text).content)
            assertEquals("Segundo párrafo", (afterBelow[3] as EditorBlock.Text).content)

            // SelectedBlockIndex is now 2. Scroll back to '+' ("Añadir"), open insertion panel, and insert Table then Quote sequentially.
            composeTestRule.onNodeWithTag("editor_main_toolbar_lazy_row")
                .performScrollToNode(hasText("Formato", ignoreCase = true).let {
                    androidx.compose.ui.test.hasContentDescription("Añadir")
                })
            composeTestRule.onNodeWithContentDescription("Añadir").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("editor_insertion_lazy_row")
                .performScrollToNode(hasText("Tabla"))
            composeTestRule.onNodeWithText("Tabla").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("editor_insertion_lazy_row")
                .performScrollToNode(hasText("Cita"))
            composeTestRule.onNodeWithText("Cita").performClick()
            composeTestRule.waitForIdle()

            val afterGraphics = parseBlocks(latestSavedNote.content)
            assertEquals(7, afterGraphics.size)
            assertTrue("Expected Table right below current paragraph (index 3)", afterGraphics[3] is EditorBlock.Table)
            assertTrue("Expected Quote directly below Table (index 4)", afterGraphics[4] is EditorBlock.Quote)
            assertEquals("Segundo párrafo", (afterGraphics[5] as EditorBlock.Text).content)
        } finally {
            db.close()
        }
    }

    @Test
    fun `TableBlock hides legacy settings header, auto-hides empty hint when typed, and opens settings on drag handle double tap`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val syncManager = DriveSyncManager(context, db)
        val tableBlock = EditorBlock.Table(
            id = "tbl_test",
            rows = 2,
            cols = 2,
            data = listOf(listOf("", ""), listOf("", ""))
        )
        var latestSavedNote = NoteEntity(
            id = "n_tbl",
            pageId = "pg_1",
            title = "Tabla Test",
            content = serializeBlocks(listOf(tableBlock))
        )

        try {
            composeTestRule.setContent {
                MyApplicationTheme {
                    NoteEditorWorkspace(
                        note = latestSavedNote,
                        aiLoading = false,
                        onDismiss = {},
                        onSave = { latestSavedNote = it },
                        onAiModify = {},
                        onOpenChatbot = {},
                        syncManager = syncManager
                    )
                }
            }
            composeTestRule.waitForIdle()

            // Legacy table header & settings gear must not exist
            composeTestRule.onNodeWithText("Mantén presionado 2s para editar", substring = true).assertDoesNotExist()
            composeTestRule.onNodeWithContentDescription("Editar tabla").assertDoesNotExist()

            // Empty hint should appear while all cells are blank (scroll into view on compact Robolectric viewport)
            composeTestRule.onNodeWithTag("table_empty_hint", useUnmergedTree = true)
                .performScrollTo()
                .assertIsDisplayed()

            // Enter data into first cell -> empty hint must auto-hide
            composeTestRule.onNodeWithTag("table_cell_tbl_test_0_0", useUnmergedTree = true)
                .performScrollTo()
                .performClick()
            composeTestRule.onNodeWithTag("table_cell_tbl_test_0_0", useUnmergedTree = true)
                .performTextInput("Dato 1")
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("table_empty_hint", useUnmergedTree = true).assertDoesNotExist()

            // Double-tapping ⠿ drag handle opens BlockSettingsBottomSheet cleanly
            composeTestRule.onNodeWithTag("block_drag_handle_0", useUnmergedTree = true)
                .performScrollTo()
                .performTouchInput { doubleClick() }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("Configuración del Elemento").assertIsDisplayed()
            composeTestRule.onNodeWithText("Filas").assertIsDisplayed()
            composeTestRule.onNodeWithText("Columnas").assertIsDisplayed()
        } finally {
            db.close()
        }
    }

    @Test
    fun `Empty image block opens settings safely on tap and hides placeholder when image is configured`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val syncManager = DriveSyncManager(context, db)
        var currentNote by mutableStateOf(
            NoteEntity(
                id = "n_media",
                pageId = "pg_1",
                title = "Media Test",
                content = serializeBlocks(listOf(EditorBlock.Image(id = "img_1", urlOrPath = "")))
            )
        )

        try {
            composeTestRule.setContent {
                MyApplicationTheme {
                    NoteEditorWorkspace(
                        note = currentNote,
                        aiLoading = false,
                        onDismiss = {},
                        onSave = { currentNote = it },
                        onAiModify = {},
                        onOpenChatbot = {},
                        syncManager = syncManager
                    )
                }
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("image_empty_placeholder", useUnmergedTree = true)
                .performScrollTo()
                .assertIsDisplayed()

            // Single tap on empty image opens Image settings without crashing
            composeTestRule.onNodeWithTag("image_empty_placeholder", useUnmergedTree = true)
                .performScrollTo()
                .performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Configuración del Elemento").assertIsDisplayed()
            composeTestRule.onNodeWithText("Seleccionar / Cambiar Imagen").assertIsDisplayed()
            composeTestRule.onNodeWithText("Pie de foto / Descripción").assertIsDisplayed()
        } finally {
            db.close()
        }
    }

    @Test
    fun `ImageBlockView and VideoBlockView automatically remove configuration hints when a media file is set`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val syncManager = DriveSyncManager(context, db)
        var imageUrl by mutableStateOf("")
        var videoUrl by mutableStateOf("")

        try {
            composeTestRule.setContent {
                MyApplicationTheme {
                    Column(modifier = Modifier.fillMaxSize()) {
                        ImageBlockView(
                            block = EditorBlock.Image(id = "img_auto", urlOrPath = imageUrl),
                            onBlockChange = {},
                            onDelete = {},
                            onOpenSettings = {},
                            syncManager = syncManager
                        )
                        VideoBlockView(
                            block = EditorBlock.Video(id = "vid_auto", sourceUrl = videoUrl),
                            onBlockChange = {},
                            onDelete = {},
                            onOpenSettings = {},
                            syncManager = syncManager
                        )
                    }
                }
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("image_empty_placeholder").assertIsDisplayed()
            composeTestRule.onNodeWithTag("video_empty_placeholder").assertIsDisplayed()

            composeTestRule.runOnUiThread {
                imageUrl = "file:///tmp/sample_photo.jpg"
                videoUrl = "file:///tmp/sample_video.mp4"
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("image_empty_placeholder").assertDoesNotExist()
            composeTestRule.onNodeWithTag("video_empty_placeholder").assertDoesNotExist()
        } finally {
            db.close()
        }
    }

    @Test
    fun `Empty video block opens settings safely on drag handle double tap without crash`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val syncManager = DriveSyncManager(context, db)
        val videoBlock = EditorBlock.Video(id = "vid_only", sourceUrl = "")
        val savedNote = NoteEntity(
            id = "n_vid",
            pageId = "pg_1",
            title = "Video Test",
            content = serializeBlocks(listOf(videoBlock))
        )

        try {
            composeTestRule.setContent {
                MyApplicationTheme {
                    NoteEditorWorkspace(
                        note = savedNote,
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

            composeTestRule.onNodeWithTag("video_empty_placeholder", useUnmergedTree = true)
                .performScrollTo()
                .assertIsDisplayed()

            composeTestRule.onNodeWithTag("block_drag_handle_0", useUnmergedTree = true)
                .performScrollTo()
                .performTouchInput { doubleClick() }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("Configuración del Elemento").assertIsDisplayed()
            composeTestRule.onNodeWithText("Seleccionar Vídeo").assertIsDisplayed()
        } finally {
            db.close()
        }
    }

    @Test
    fun `Collapsible header Tab indentation increments indentLevel before chevron without adding spaces to text`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val syncManager = DriveSyncManager(context, db)
        val header = EditorBlock.Text(
            id = "hdr_1",
            content = "Título contraíble",
            isCollapsedHeader = true,
            isCollapsed = false,
            isHeader = true,
            fontSize = 18,
            isBold = true,
            indentLevel = 0
        )
        var latestSavedNote = NoteEntity(
            id = "n_hdr",
            pageId = "pg_1",
            title = "Header Test",
            content = serializeBlocks(listOf(header))
        )

        try {
            composeTestRule.setContent {
                MyApplicationTheme {
                    NoteEditorWorkspace(
                        note = latestSavedNote,
                        aiLoading = false,
                        onDismiss = {},
                        onSave = { latestSavedNote = it },
                        onAiModify = {},
                        onOpenChatbot = {},
                        syncManager = syncManager
                    )
                }
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("editor_collapsible_toggle_hdr_1").assertIsDisplayed()

            // Select the collapsible header and trigger Tab/Indent from toolbar
            composeTestRule.onNodeWithTag("text_block_0").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("editor_main_toolbar_lazy_row")
                .performScrollToNode(androidx.compose.ui.test.hasContentDescription("Dar espaciado / Bajar nivel a sub-lista"))
            composeTestRule.onNodeWithContentDescription("Dar espaciado / Bajar nivel a sub-lista").performClick()
            composeTestRule.waitForIdle()

            val updated = parseBlocks(latestSavedNote.content).first() as EditorBlock.Text
            assertEquals(1, updated.indentLevel)
            assertEquals("Título contraíble", updated.content)
        } finally {
            db.close()
        }
    }
}
