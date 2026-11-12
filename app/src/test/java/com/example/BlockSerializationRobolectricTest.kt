package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BlockSerializationRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `serializeBlocks and parseBlocks persist parentToggleId across all 11 block types`() {
        val parent = EditorBlock.Text(
            id = "toggle-1",
            content = "Sección",
            isHeader = true,
            isCollapsedHeader = true,
            isBold = true
        )
        val childText = EditorBlock.Text(content = "Texto hijo", parentToggleId = parent.id)
        val childTable = EditorBlock.Table(rows = 2, cols = 2, data = listOf(listOf("A", "B"), listOf("1", "2")), parentToggleId = parent.id)
        val childImage = EditorBlock.Image(urlOrPath = "file://img.png", caption = "Foto", parentToggleId = parent.id)
        val childAudio = EditorBlock.Audio(name = "voz.m4a", duration = "01:00", sourceUrl = "", parentToggleId = parent.id)
        val childVideo = EditorBlock.Video(title = "clip.mp4", length = "02:00", sourceUrl = "", parentToggleId = parent.id)
        val childFile = EditorBlock.File(name = "doc.pdf", sourceUrl = "", size = "10 KB", parentToggleId = parent.id)
        val childTodo = EditorBlock.Todo(content = "Tarea hija", isChecked = true, parentToggleId = parent.id)
        val childCallout = EditorBlock.Callout(content = "Nota", emoji = "💡", colorVariant = "Purple", parentToggleId = parent.id)
        val childQuote = EditorBlock.Quote(content = "Cita hija", parentToggleId = parent.id)
        val childDivider = EditorBlock.Divider(style = "Solid", parentToggleId = parent.id)
        val childCode = EditorBlock.Code(code = "println(1)", language = "Kotlin", parentToggleId = parent.id)

        val blocks = listOf(
            parent,
            childText,
            childTable,
            childImage,
            childAudio,
            childVideo,
            childFile,
            childTodo,
            childCallout,
            childQuote,
            childDivider,
            childCode
        )

        val serialized = serializeBlocks(blocks)
        val restored = parseBlocks(serialized)

        assertEquals(blocks.size, restored.size)
        assertEquals("toggle-1", restored[0].id)
        assertNull(restored[0].parentToggleId)
        for (idx in 1 until restored.size) {
            assertEquals("Expected parentToggleId preserved at index $idx", parent.id, restored[idx].parentToggleId)
        }
    }

    @Test
    fun `MarkdownChatBubble cleans duplicate hashes and expands collapsible details interactively`() {
        val sample = """
            # # Bitácora Limpia

            <details><summary>I. Origen</summary>

            Contenido desplegable oculto inicialmente

            </details>
        """.trimIndent()

        composeTestRule.setContent {
            MyApplicationTheme {
                MarkdownChatBubble(
                    content = sample,
                    notesList = emptyList(),
                    onNoteClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Bitácora Limpia").assertIsDisplayed()
        composeTestRule.onNodeWithText("I. Origen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Contenido desplegable oculto inicialmente").assertDoesNotExist()

        composeTestRule.onNodeWithTag("chat_collapsible_toggle").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Contenido desplegable oculto inicialmente").assertIsDisplayed()
    }
}
