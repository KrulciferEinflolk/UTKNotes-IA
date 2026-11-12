package com.example

import com.example.util.ChatNotePipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatNotePipelineUnitTest {

    @Test
    fun `detectCreateNoteIntent recognizes natural Spanish and English note creation requests`() {
        assertTrue(
            ChatNotePipeline.detectCreateNoteIntent(
                "pásalo a una nota conservando el estilo, espaciado, títulos, subtitulos desplegables, formato etc"
            )
        )
        assertTrue(ChatNotePipeline.detectCreateNoteIntent("pásalo a una nota (el contenido manteniendo el formato)"))
        assertTrue(ChatNotePipeline.detectCreateNoteIntent("convierte a nota"))
        assertTrue(ChatNotePipeline.detectCreateNoteIntent("crea una nota con este resumen"))
        assertTrue(ChatNotePipeline.detectCreateNoteIntent("guarda en una nueva nota"))
        assertTrue(ChatNotePipeline.detectCreateNoteIntent("convert this into a new note"))

        assertFalse(ChatNotePipeline.detectCreateNoteIntent("hola"))
        assertFalse(ChatNotePipeline.detectCreateNoteIntent("resume esta nota"))
        assertFalse(ChatNotePipeline.detectCreateNoteIntent("dame un resumen de la lectura"))
        assertFalse(ChatNotePipeline.detectCreateNoteIntent("explica el contenido del pdf"))
    }

    @Test
    fun `detectActiveNoteReference distinguishes new note creation from referencing active editor note`() {
        assertFalse(
            ChatNotePipeline.detectActiveNoteReference(
                "pásalo a una nota conservando el estilo, espaciado, títulos, subtitulos desplegables, formato etc"
            )
        )
        assertFalse(ChatNotePipeline.detectActiveNoteReference("pásalo a una nota (el contenido manteniendo el formato)"))
        assertFalse(ChatNotePipeline.detectActiveNoteReference("crea una nota sobre el origen de los elfos"))

        assertTrue(ChatNotePipeline.detectActiveNoteReference("resume esta nota"))
        assertTrue(ChatNotePipeline.detectActiveNoteReference("modifica mi nota con esta sección"))
        assertTrue(ChatNotePipeline.detectActiveNoteReference("agrega esto a la nota actual"))
        assertTrue(ChatNotePipeline.detectActiveNoteReference("guarda esto en esta nota"))
    }

    @Test
    fun `detectPreserveFormatIntent identifies formatting and collapsible instructions`() {
        assertTrue(
            ChatNotePipeline.detectPreserveFormatIntent(
                "pásalo a una nota conservando el estilo, espaciado, títulos, subtitulos desplegables, formato etc"
            )
        )
        assertTrue(ChatNotePipeline.detectPreserveFormatIntent("manteniendo el formato tal cual"))
        assertFalse(ChatNotePipeline.detectPreserveFormatIntent("hola aura, ¿qué puedes hacer?"))
    }

    @Test
    fun `extractThoughtAndBody never hijacks H1 headings or intros when think tags are absent`() {
        val sampleWithH1 = """
            # Bitácora de Observación: El Surgimiento de la Especie Elfa

            ## 1. El Origen y la Ascensión

            Los elfos surgieron de un modo inexplicable.
        """.trimIndent()

        val (thought1, body1) = ChatNotePipeline.extractThoughtAndBody(sampleWithH1)
        assertEquals("", thought1)
        assertTrue(body1.startsWith("# Bitácora de Observación: El Surgimiento de la Especie Elfa"))

        val sampleWithChronicle = """
            # Notas de Observación: La Crónica de los Elfos

            I. Origen y Evolución Temprana
        """.trimIndent()
        val (thought2, body2) = ChatNotePipeline.extractThoughtAndBody(sampleWithChronicle)
        assertEquals("", thought2)
        assertTrue(body2.startsWith("# Notas de Observación: La Crónica de los Elfos"))

        val introSample = "Aquí tienes la transcripción del documento.\n\nContenido principal."
        val (thought3, body3) = ChatNotePipeline.extractThoughtAndBody(introSample)
        assertEquals("", thought3)
        assertEquals(introSample, body3)
    }

    @Test
    fun `extractThoughtAndBody separates genuine think blocks cleanly`() {
        val raw = "<think>Verificando estructura de capítulos.</think>\n\n# Crónica\n\nTexto final."
        val (thought, body) = ChatNotePipeline.extractThoughtAndBody(raw)
        assertEquals("Verificando estructura de capítulos.", thought)
        assertEquals("# Crónica\n\nTexto final.", body)

        val unclosed = "<think>Analizando título\n\n# Título Real\n\nCuerpo"
        val (thoughtUnclosed, bodyUnclosed) = ChatNotePipeline.extractThoughtAndBody(unclosed)
        assertEquals("Analizando título", thoughtUnclosed)
        assertEquals("# Título Real\n\nCuerpo", bodyUnclosed)

        val closeOnly = "Pensamiento previo</think>\nRespuesta limpia"
        val (thoughtCloseOnly, bodyCloseOnly) = ChatNotePipeline.extractThoughtAndBody(closeOnly)
        assertEquals("Pensamiento previo", thoughtCloseOnly)
        assertEquals("Respuesta limpia", bodyCloseOnly)
    }

    @Test
    fun `sanitizeThoughtText returns empty on blank and keeps legitimate Spanish words`() {
        assertEquals("", ChatNotePipeline.sanitizeThoughtText("   \n  "))
        val genuine = "Mantendré el formato y los subtítulos desplegables del texto."
        assertEquals(genuine, ChatNotePipeline.sanitizeThoughtText(genuine))
        val leaked = "Alright, so I need to do this\nMantendré el formato original."
        assertEquals("Mantendré el formato original.", ChatNotePipeline.sanitizeThoughtText(leaked))
    }

    @Test
    fun `cleanHeadingText removes duplicate hashes and surrounding bold or underline markers`() {
        assertEquals("Título limpio", ChatNotePipeline.cleanHeadingText("# # Título limpio"))
        assertEquals("Subtítulo", ChatNotePipeline.cleanHeadingText("## **Subtítulo**"))
        assertEquals("Otro", ChatNotePipeline.cleanHeadingText("### __Otro__"))
    }

    @Test
    fun `extractTitleFromNoteMarkdown picks clean heading without tags or markers`() {
        val md = """
            <think>Pensando...</think>
            [NOTE_CREATED:id123|Viejo]
            # [color:Purple]<u>Bitácora de Observación</u>[/color]

            Contenido
        """.trimIndent()
        assertEquals("Bitácora de Observación", ChatNotePipeline.extractTitleFromNoteMarkdown(md))
    }

    @Test
    fun `buildNoteCreatedMarker and stripNoteCreatedMarker round trip safely`() {
        val marker = ChatNotePipeline.buildNoteCreatedMarker("note-42", "Bitácora | Parte [1]")
        val combined = "$marker\n\n# Bitácora | Parte [1]\n\nCuerpo"
        val (stripped, info) = ChatNotePipeline.stripNoteCreatedMarker(combined)
        assertNotNull(info)
        assertEquals("note-42", info?.first)
        assertEquals("Bitácora - Parte (1)", info?.second)
        assertEquals("# Bitácora | Parte [1]\n\nCuerpo", stripped)
    }

    @Test
    fun `resolveMentionedNoteTitle extracts quoted and unquoted mentions`() {
        val candidates = ChatNotePipeline.resolveMentionedNoteTitle(
            """Usa @"Bitácora de los Elfos" y compárala con @Inicio"""
        )
        assertTrue(candidates.contains("Bitácora de los Elfos"))
        assertTrue(candidates.any { it.startsWith("Inicio") })
    }

    @Test
    fun `parseSingleTextBlock peels whole wrappers but preserves partial inline styling`() {
        val fullStyled = parseSingleTextBlock("**[color:Purple]<u>Destacado</u>[/color]**")
        assertTrue(fullStyled.isBold)
        assertTrue(fullStyled.isUnderline)
        assertEquals("Purple", fullStyled.fontColor)
        assertEquals("Destacado", fullStyled.content)

        val partialBold = parseSingleTextBlock("**Capacidad de adaptación:** Sobrevivieron a glaciaciones.")
        assertFalse(partialBold.isBold)
        assertEquals("**Capacidad de adaptación:** Sobrevivieron a glaciaciones.", partialBold.content)

        val twoBoldSpans = parseSingleTextBlock("**Inicio** en el centro **Fin**")
        assertFalse(twoBoldSpans.isBold)
        assertEquals("**Inicio** en el centro **Fin**", twoBoldSpans.content)
    }

    @Test
    fun `parseTextContentToBlocks parses details collapsibles nested hierarchy and arrow toggles`() {
        val md = """
            # # Bitácora Principal

            <details><summary>I. Origen Temprano</summary>

            Texto introductorio del capítulo.

            - Primer rasgo

            <details><summary>Subsección Oculta</summary>

            Detalle anidado.

            </details>

            </details>

            ## ▼ Sección con Flecha

            > Cita importante

            - [x] Tarea completada
        """.trimIndent()

        val blocks = parseTextContentToBlocks(md, splitLinesAsBlocks = false)

        val h1 = blocks.first() as EditorBlock.Text
        assertTrue(h1.isHeader)
        assertEquals(24, h1.fontSize)
        assertEquals("Bitácora Principal", h1.content)
        assertNull(h1.parentToggleId)

        val outerToggle = blocks.first { it is EditorBlock.Text && it.content == "I. Origen Temprano" } as EditorBlock.Text
        assertTrue(outerToggle.isCollapsedHeader)

        val innerParagraph = blocks.first { it is EditorBlock.Text && it.content == "Texto introductorio del capítulo." } as EditorBlock.Text
        assertEquals(outerToggle.id, innerParagraph.parentToggleId)

        val innerBullet = blocks.first { it is EditorBlock.Text && it.isBullet && it.content == "Primer rasgo" } as EditorBlock.Text
        assertEquals(outerToggle.id, innerBullet.parentToggleId)

        val nestedToggle = blocks.first { it is EditorBlock.Text && it.content == "Subsección Oculta" } as EditorBlock.Text
        assertTrue(nestedToggle.isCollapsedHeader)
        assertEquals(outerToggle.id, nestedToggle.parentToggleId)

        val nestedDetail = blocks.first { it is EditorBlock.Text && it.content == "Detalle anidado." } as EditorBlock.Text
        assertEquals(nestedToggle.id, nestedDetail.parentToggleId)

        val arrowToggle = blocks.first { it is EditorBlock.Text && it.content == "Sección con Flecha" } as EditorBlock.Text
        assertTrue(arrowToggle.isCollapsedHeader)
        assertEquals(20, arrowToggle.fontSize)

        val quote = blocks.first { it is EditorBlock.Quote } as EditorBlock.Quote
        assertEquals("Cita importante", quote.content)
        assertEquals(arrowToggle.id, quote.parentToggleId)

        val todo = blocks.first { it is EditorBlock.Todo } as EditorBlock.Todo
        assertTrue(todo.isChecked)
        assertEquals("Tarea completada", todo.content)
        assertEquals(arrowToggle.id, todo.parentToggleId)
    }

    @Test
    fun `parseTextContentToBlocks preserves double blank spacers and splitLinesAsBlocks option`() {
        val mdWithSpacing = "Párrafo uno.\n\n\nPárrafo dos.\nLínea siguiente."
        val merged = parseTextContentToBlocks(mdWithSpacing, splitLinesAsBlocks = false)
        assertTrue(merged.any { it is EditorBlock.Text && it.content.isBlank() })
        assertTrue(merged.any { it is EditorBlock.Text && it.content == "Párrafo dos. Línea siguiente." })

        val split = parseTextContentToBlocks("Línea A\nLínea B", splitLinesAsBlocks = true)
        assertEquals(2, split.size)
        assertEquals("Línea A", (split[0] as EditorBlock.Text).content)
        assertEquals("Línea B", (split[1] as EditorBlock.Text).content)
    }

    @Test
    fun `stripLeadingDuplicateTitleBlock and formatBlocksListToMarkdown round trip cleanly`() {
        val md = """
            # Crónica Elfa

            <details><summary>Capítulo 1</summary>

            Relato inicial.

            - Detalle clave

            </details>
        """.trimIndent()

        val blocks = stripLeadingDuplicateTitleBlock(parseTextContentToBlocks(md), "Crónica Elfa")
        assertTrue(blocks.first() is EditorBlock.Text && (blocks.first() as EditorBlock.Text).isCollapsedHeader)

        val formatted = formatBlocksListToMarkdown(blocks)
        assertTrue(formatted.contains("<details><summary>Capítulo 1</summary>"))
        assertTrue(formatted.contains("Relato inicial."))
        assertTrue(formatted.contains("- Detalle clave"))
        assertTrue(formatted.contains("</details>"))
    }

    @Test
    fun `parseTextContentToBlocks initializes collapsible sections as isCollapsed = true`() {
        val md = """
            <details><summary>Un último acto de amor.</summary>

            Párrafo interno.

            </details>

            ## ▼ Tu voluntad vive conmigo.
            Texto con flecha.
        """.trimIndent()
        val blocks = parseTextContentToBlocks(md)
        val detailsHeader = blocks.first { it is EditorBlock.Text && it.content == "Un último acto de amor." } as EditorBlock.Text
        assertTrue(detailsHeader.isCollapsedHeader)
        assertTrue("Details section must be collapsed initially", detailsHeader.isCollapsed)

        val arrowHeader = blocks.first { it is EditorBlock.Text && it.content == "Tu voluntad vive conmigo." } as EditorBlock.Text
        assertTrue(arrowHeader.isCollapsedHeader)
        assertTrue("Arrow section must be collapsed initially", arrowHeader.isCollapsed)
    }

    @Test
    fun `parseTextContentToBlocks parses pipe quote lines as EditorBlock Quote`() {
        val md = """
            ## ▼ Subtítulo
            | Cuida y protege a los seres que aún quedan en este mundo.
            > Otra cita normal.
        """.trimIndent()
        val blocks = parseTextContentToBlocks(md)
        val quotes = blocks.filterIsInstance<EditorBlock.Quote>()
        assertEquals(2, quotes.size)
        assertEquals("Cuida y protege a los seres que aún quedan en este mundo.", quotes[0].content)
        assertNotNull(quotes[0].parentToggleId)
        assertEquals("Otra cita normal.", quotes[1].content)
    }

    @Test
    fun `PdfStructureExtractor processPageElements maintains parent toggle across pages and marks collapsed`() {
        val pageState = com.example.util.PdfMultiPageState()

        // Page 1: Dropdown heading starts
        val p1Elements = listOf(
            com.example.util.ExtractedElement.Text("▼ Un último acto de amor.", 50f, 700f, 18f, isBold = true),
            com.example.util.ExtractedElement.Text("Párrafo en página 3.", 50f, 650f, 14f)
        )
        val p1Blocks = com.example.util.PdfStructureExtractor.processPageElements(p1Elements, emptyList(), pageState)
        val toggleBlock = p1Blocks.first { it is EditorBlock.Text && it.isCollapsedHeader } as EditorBlock.Text
        assertTrue("Toggle header must start collapsed", toggleBlock.isCollapsed)
        assertNotNull("PageState must retain activeToggleId", pageState.activeToggleId)
        assertEquals(toggleBlock.id, pageState.activeToggleId)

        // Page 2: Continuation on page 4 without repeating toggle header
        val p2Elements = listOf(
            com.example.util.ExtractedElement.Text("• Diálogo continuo en página 4", 65f, 700f, 14f)
        )
        val p2Blocks = com.example.util.PdfStructureExtractor.processPageElements(p2Elements, emptyList(), pageState)
        assertEquals(1, p2Blocks.size)
        val childBullet = p2Blocks[0] as EditorBlock.Text
        assertTrue(childBullet.isBullet)
        assertEquals("Child on page 2 must belong to parent toggle from page 1", toggleBlock.id, childBullet.parentToggleId)

        // Page 3: QuoteBar on page 8
        val quoteBarMarker = com.example.util.ExtractedMarker.QuoteBar(45f, 600f, height = 30f)
        val p3Elements = listOf(
            com.example.util.ExtractedElement.Text("Cuida y protege a las criaturas...", 55f, 600f, 14f)
        )
        val p3Blocks = com.example.util.PdfStructureExtractor.processPageElements(p3Elements, listOf(quoteBarMarker), pageState)
        assertEquals(1, p3Blocks.size)
        assertTrue("Element next to quote bar must be an EditorBlock.Quote", p3Blocks[0] is EditorBlock.Quote)
        assertEquals(toggleBlock.id, p3Blocks[0].parentToggleId)
    }

    @Test
    fun `PdfStructureExtractor preserves literal Narrador asterisks without mangling`() {
        val elements = listOf(
            com.example.util.ExtractedElement.Text("Narrador**", 50f, 700f, 15f, isBold = true),
            com.example.util.ExtractedElement.Text("De el cielo los elfos lograron a precias una fuente de luz.", 50f, 670f, 14f)
        )
        val blocks = com.example.util.PdfStructureExtractor.processPageElements(elements)
        val narrador = blocks.first { it is EditorBlock.Text && it.content.contains("Narrador") } as EditorBlock.Text
        assertEquals("Narrador**", narrador.content)
        assertTrue(narrador.isBold)
        assertFalse(narrador.isHeader)
    }

    @Test
    fun `parseInlineMarkdown preserves Narrador asterisks without bolding subsequent text`() {
        val sample = "Narrador**\nDe el cielo los elfos lograron ver la luz."
        val parsed = parseInlineMarkdown(sample)
        assertTrue(parsed.text.contains("Narrador**"))
        val spans = parsed.spanStyles
        val extraBoldOnFollowingText = spans.any {
            it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.ExtraBold &&
            it.start >= sample.indexOf("De el cielo")
        }
        assertFalse("Trailing asterisks on Narrador** must not cause subsequent paragraph to become bold", extraBoldOnFollowingText)
    }

}
