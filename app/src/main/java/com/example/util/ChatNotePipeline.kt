package com.example.util

import java.text.Normalizer

/**
 * Pure-Kotlin helpers for Aura's chat-to-note pipeline:
 * - Natural Spanish/English intent detection (create note vs query active note vs preserve formatting)
 * - Accurate <think>...</think> extraction without hijacking H1 titles or intros
 * - Safe thought sanitization and note-creation marker parsing
 * - Agent system prompt construction tuned for faithful note preservation and collapsible subtitles
 */
object ChatNotePipeline {

    private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val NOTE_CREATED_MARKER_REGEX = Regex("""\[NOTE_CREATED:([^|\]]+)\|([^\]]*)\]""")

    private val ACTIVE_NOTE_PATTERNS = listOf(
        Regex("""\b(esta|la\s+actual|la\s+abierta|mi|nuestra|esa|aquella|dicha)\s+nota\b"""),
        Regex("""\bnota\s+(actual|abierta|seleccionada|activa|anterior)\b"""),
        Regex("""\b(en|de|sobre|desde|a)\s+la\s+nota\b"""),
        Regex("""\b(resume|resumir|resumeme|analiza|analizar|explica|explicame|modifica|modificar|edita|editar|actualiza|actualizar|mejora|mejorar|corrige|corregir|continua|continuar|agrega|agregar|anade|anadir|inserta|insertar|reemplaza|reemplazar)\s+(la\s+|esta\s+|mi\s+)?nota\b""")
    )

    private val CREATE_NOTE_PATTERNS = listOf(
        Regex("""\b(pasa|pasalo|pasala|pasame|pasarlo|pasarla|pasar)\s+(?:todo\s+|esto\s+|eso\s+|el\s+contenido\s+|la\s+informacion\s+|el\s+texto\s+|lo\s+anterior\s+|lo\s+de\s+arriba\s+)?(?:a|en|hacia|como)\s+(?:una\s+)?(?:nueva\s+)?nota\b"""),
        Regex("""\b(convierte|conviertelo|conviertela|convertir|transforma|transformalo|transformala|transformar|lleva|llevalo|llevala|exporta|exportalo|exportala|manda|mandalo|mandala|pon|ponlo|ponla|pone|copia|copialo|copiala|vuelca)\s+(?:todo\s+|esto\s+|eso\s+|el\s+contenido\s+|el\s+texto\s+)?(?:a|en|como)\s+(?:una\s+)?(?:nueva\s+)?nota\b"""),
        Regex("""\b(guarda|guardalo|guardala|guardar|almacena|registra|archiva)\s+(?:todo\s+|esto\s+|eso\s+|el\s+contenido\s+|esta\s+respuesta\s+)?(?:a|en|como)\s+(?:una\s+)?(?:nueva\s+)?nota\b"""),
        Regex("""\bguarda(?:r)?\s+esta\s+nota\b"""),
        Regex("""\b(crea|creame|crear|genera|generame|generar|haz|hazme|hacer|arma|armame|armar|redacta|redactame|redactar|escribe|escribeme|escribir|elabora|construye|estructura)\s+(?:y\s+guarda(?:r)?\s+)?(?:una\s+)?(?:nueva\s+)?nota\b"""),
        Regex("""\bnueva\s+nota\s+(?:con|sobre|de|a\s+partir|usando)\b"""),
        Regex("""\b(?:turn|convert|save|put|make|create|export)\s+(?:this|it|that)?\s*(?:into|to|in|as)\s+a\s+(?:new\s+)?note\b""")
    )

    private val PRESERVE_FORMAT_PATTERNS = listOf(
        Regex("""\bformato\b"""),
        Regex("""\bestilo\b"""),
        Regex("""\bespaciado\b"""),
        Regex("""\bdesplegable"""),
        Regex("""\bcontraible"""),
        Regex("""\bplegable"""),
        Regex("""\btitulo"""),
        Regex("""\bsubtitulo"""),
        Regex("""\btal\s+cual\b"""),
        Regex("""\btranscri"""),
        Regex("""\bfielmente\b"""),
        Regex("""\bliteral"""),
        Regex("""\bestructura\b"""),
        Regex("""\bmaquetacion\b"""),
        Regex("""\bexacto\b"""),
        Regex("""\bexacta\b"""),
        Regex("""\bconservando\b"""),
        Regex("""\bmanteniendo\b"""),
        Regex("""\brespetando\b""")
    )

    private val INDEPENDENT_TOPIC_PATTERNS = listOf(
        Regex("""^(dame\s+un\s+resumen|resumen|explica|que\s+es)\b""")
    )

    fun normalizeForIntent(input: String): String {
        val normalized = Normalizer.normalize(input.trim().lowercase(), Normalizer.Form.NFD)
        return DIACRITICS_REGEX.replace(normalized, "").replace(Regex("""\s+"""), " ")
    }

    fun detectCreateNoteIntent(userPrompt: String): Boolean {
        val norm = normalizeForIntent(userPrompt)
        if (norm.isBlank()) return false
        return CREATE_NOTE_PATTERNS.any { it.containsMatchIn(norm) }
    }

    fun detectActiveNoteReference(userPrompt: String): Boolean {
        val norm = normalizeForIntent(userPrompt)
        if (norm.isBlank()) return false
        if (detectCreateNoteIntent(userPrompt)) {
            // Only treat as referencing the active editor note if explicit deictic cues appear
            // ("esta nota", "mi nota", "nota actual") rather than "a una nota".
            return Regex("""\b(esta|mi|nuestra|la\s+actual|la\s+abierta|nota\s+actual|nota\s+abierta|nota\s+activa)\b""").containsMatchIn(norm)
        }
        return ACTIVE_NOTE_PATTERNS.any { it.containsMatchIn(norm) }
    }

    fun detectPreserveFormatIntent(userPrompt: String): Boolean {
        val norm = normalizeForIntent(userPrompt)
        if (norm.isBlank()) return false
        return PRESERVE_FORMAT_PATTERNS.any { it.containsMatchIn(norm) }
    }

    fun isIndependentGeneralTopic(userPrompt: String): Boolean {
        val norm = normalizeForIntent(userPrompt)
        if (norm.isBlank()) return false
        if (norm.contains("documento") || norm.contains("archivo") || norm.contains("nota") || norm.contains("formato")) {
            return false
        }
        return INDEPENDENT_TOPIC_PATTERNS.any { it.containsMatchIn(norm) }
    }

    fun sanitizeTechnicalTags(text: String): String {
        return text.replace(
            Regex("</?(assistant_answer|full_note|user_request|cursor_position|selected_text|active_note_preview|attached_file|think)>", RegexOption.IGNORE_CASE),
            ""
        ).trim()
    }

    /**
     * Separates real model thinking (<think>...</think> or text before </think>) from the final answer.
     * NEVER guesses thoughts from Markdown headings (#, ##, ###) because doing so steals H1 titles
     * and introductory text into the reasoning card.
     */
    fun extractThoughtAndBody(rawResponse: String): Pair<String, String> {
        val workingRaw = rawResponse.trim()
        if (workingRaw.isEmpty()) return "" to ""

        val extractedThought: String
        var bodyWithoutThink: String

        if (workingRaw.contains("<think>", ignoreCase = true)) {
            val startIdx = workingRaw.indexOf("<think>", ignoreCase = true)
            val endIdx = workingRaw.indexOf("</think>", startIndex = startIdx, ignoreCase = true)
            if (endIdx != -1) {
                extractedThought = workingRaw.substring(startIdx + "<think>".length, endIdx).trim()
                val before = workingRaw.substring(0, startIdx).trim()
                val after = workingRaw.substring(endIdx + "</think>".length).trim()
                bodyWithoutThink = listOf(before, after).filter { it.isNotEmpty() }.joinToString("\n\n")
            } else {
                // Unclosed <think>: keep text before <think> if present; extract thought without eating Markdown headings
                val rawAfterTag = workingRaw.substring(startIdx + "<think>".length).trim()
                val headerSplit = Regex("""(?m)^#{1,6}\s+""").find(rawAfterTag)
                if (headerSplit != null && headerSplit.range.first > 0) {
                    extractedThought = rawAfterTag.substring(0, headerSplit.range.first).trim()
                    val before = workingRaw.substring(0, startIdx).trim()
                    val afterHeader = rawAfterTag.substring(headerSplit.range.first).trim()
                    bodyWithoutThink = listOf(before, afterHeader).filter { it.isNotEmpty() }.joinToString("\n\n")
                } else {
                    extractedThought = rawAfterTag
                    bodyWithoutThink = workingRaw.substring(0, startIdx).trim()
                }
            }
        } else if (workingRaw.contains("</think>", ignoreCase = true)) {
            val endIdx = workingRaw.indexOf("</think>", ignoreCase = true)
            extractedThought = workingRaw.substring(0, endIdx).trim()
            bodyWithoutThink = workingRaw.substring(endIdx + "</think>".length).trim()
        } else {
            extractedThought = ""
            bodyWithoutThink = workingRaw
        }

        bodyWithoutThink = sanitizeTechnicalTags(bodyWithoutThink)
        return sanitizeThoughtText(extractedThought) to bodyWithoutThink
    }

    /**
     * Cleans genuine thought content without fabricating placeholder thoughts when blank
     * and without dropping ordinary Spanish content lines mentioning words like "formato".
     */
    fun sanitizeThoughtText(rawThought: String): String {
        val cleanedTags = sanitizeTechnicalTags(rawThought)
        if (cleanedTags.isBlank()) return ""

        val filteredLines = cleanedTags.lines().filter { line ->
            val lower = line.lowercase()
            val isMetaLeak = lower.contains("assistant_answer") ||
                lower.contains("full_note") ||
                lower.contains("system_instruction") ||
                lower.contains("json schema") ||
                lower.contains("<attached_file>") ||
                lower.contains("i can't think in english") ||
                lower.contains("oro rule") ||
                lower.contains("definicion de la asistente") ||
                lower.contains("definición de la asistente") ||
                lower.contains("propósito y funcionalidad prioritaria") ||
                lower.contains("alright, so i need")
            !isMetaLeak
        }.map { it.trimEnd() }

        return filteredLines.joinToString("\n").trim()
    }

    fun cleanHeadingText(rawHeading: String): String {
        var clean = rawHeading.trim()
        clean = clean.replace(Regex("""^#{1,6}\s*"""), "").trim()
        clean = clean.replace(Regex("""^#{1,6}\s*"""), "").trim()
        if (clean.startsWith("***") && clean.endsWith("***") && clean.length > 6) {
            clean = clean.removeSurrounding("***").trim()
        } else if (clean.startsWith("**") && clean.endsWith("**") && clean.length > 4) {
            clean = clean.removeSurrounding("**").trim()
        } else if (clean.startsWith("__") && clean.endsWith("__") && clean.length > 4) {
            clean = clean.removeSurrounding("__").trim()
        }
        clean = clean.replace(Regex("""^#{1,6}\s*"""), "").trim()
        return clean
    }

    fun extractTitleFromNoteMarkdown(markdown: String, fallback: String = "Nota de Aura"): String {
        val cleaned = sanitizeTechnicalTags(extractThoughtAndBody(markdown).second)
            .let { stripNoteCreatedMarker(it).first }
            .trim()
        if (cleaned.isBlank()) return fallback

        val nonBlankLines = cleaned.lines().map { it.trim() }.filter { it.isNotBlank() && it != "---" && it != "***" }
        val h1Line = nonBlankLines.firstOrNull { Regex("""^#(?!#)\s+.*""").matches(it) }
        val anyHeadingLine = nonBlankLines.firstOrNull { it.startsWith("#") }
        val summaryLine = nonBlankLines.firstOrNull {
            it.startsWith("<details>", ignoreCase = true) ||
                it.startsWith("<summary>", ignoreCase = true) ||
                it.startsWith("▼") || it.startsWith("▶")
        }

        val rawCandidate = when {
            h1Line != null -> cleanHeadingText(h1Line)
            anyHeadingLine != null -> cleanHeadingText(anyHeadingLine)
            summaryLine != null -> summaryLine
                .replace(Regex("""</?(details|summary|b|strong|i|em)>""", RegexOption.IGNORE_CASE), "")
                .removePrefix("▼").removePrefix("▶").removePrefix("▾").removePrefix("▸")
                .let { cleanHeadingText(it) }
            else -> nonBlankLines.firstOrNull()
                ?.replace(Regex("""\[color:[^\]]+\]|\[/color\]|</?u>|\*\*|\*|__|~~"""), "")
                ?.take(48)
                ?.trim()
                ?: fallback
        }

        val finalTitle = rawCandidate
            .replace(Regex("""\[color:[^\]]+\]|\[/color\]|</?u>"""), "")
            .trim()
            .take(80)

        return if (finalTitle.length < 2 || finalTitle.contains("assistantanswer", ignoreCase = true)) {
            fallback
        } else {
            finalTitle
        }
    }

    fun buildNoteCreatedMarker(noteId: String, title: String): String {
        val safeTitle = title.replace("[", "(").replace("]", ")").replace("|", "-").trim()
        return "[NOTE_CREATED:$noteId|$safeTitle]"
    }

    fun stripNoteCreatedMarker(text: String): Pair<String, Pair<String, String>?> {
        val match = NOTE_CREATED_MARKER_REGEX.find(text) ?: return text.trim() to null
        val noteId = match.groupValues[1].trim()
        val noteTitle = match.groupValues[2].trim()
        val stripped = text.replace(match.value, "").trim()
        return stripped to (noteId to noteTitle)
    }

    fun resolveMentionedNoteTitle(userPrompt: String): List<String> {
        val candidates = mutableListOf<String>()
        val quotedRegex = Regex("""@"([^"]+)"""")
        for (m in quotedRegex.findAll(userPrompt)) {
            m.groupValues[1].trim().takeIf { it.isNotEmpty() }?.let { candidates.add(it) }
        }
        val simpleRegex = Regex("""@([A-Za-z0-9áéíóúÁÉÍÓÚñÑ_\-]+(?:\s+[A-Za-z0-9áéíóúÁÉÍÓÚñÑ_\-]+){0,3})""")
        for (m in simpleRegex.findAll(userPrompt)) {
            m.groupValues[1].trim().takeIf { it.isNotEmpty() }?.let { candidates.add(it) }
        }
        return candidates.distinct()
    }

    fun buildSystemPrompt(
        userQuery: String,
        isGoogleAiStudio: Boolean,
        isSimpleQuery: Boolean,
        isComplexQuery: Boolean
    ): String {
        val wantsCreateNote = detectCreateNoteIntent(userQuery)
        val wantsPreserveFormat = detectPreserveFormatIntent(userQuery)
        val isNoteStructuringTask = wantsCreateNote || wantsPreserveFormat

        val formattingContract = """
FORMATOS SOPORTADOS POR UTK NOTES (úsalos con precisión):
- Título principal: una sola línea con `# Título` (nunca escribas `# # Título`).
- Subtítulos normales: `## Subtítulo` o `### Encabezado`.
- Subtítulos o secciones desplegables: usa EXACTAMENTE bloques HTML:
  <details><summary>Título del subtítulo desplegable</summary>

  Contenido interno (párrafos, diálogos con viñetas `- `, sub-viñetas con sangría `    - `, citas `> ` o `| `, tareas `- [ ]`).

  </details>
  (Usa `<details><summary>...</summary>` siempre que el usuario pida desplegables o el documento original tenga subtítulos con flechas ▼/▶).
- Estilos inline y de bloque: `**negrita**`, `*cursiva*`, `<u>subrayado</u>`, `[color:Purple]texto[/color]` (colores válidos: `Purple`, `Blue`, `Green`, `Red`, `Amber`, `Cyan`, `Pink`).
- Citas y barras laterales: usa `> cita` o `| cita` para textos enmarcados con barra lateral.
- Marcas especiales y nombres: preserva marcas textuales del original como `Narrador**` tal como están.
- Listas con sangría: `- elemento` y sub-elementos con 4 espacios (`    - sub-elemento`), listas numeradas `1. `, tareas `- [ ]` / `- [x]`, divisores `---`.
- Espaciado: separa bloques y párrafos con una línea en blanco.
""".trimIndent()

        val taskSpecificRules = when {
            isNoteStructuringTask -> """
MODO ESTRUCTURACIÓN / CREACIÓN DE NOTA:
1. PROHIBIDO escribir introducciones conversacionales, saludos ni despedidas ("Aquí tienes...", "Por supuesto...", ni un "---" inicial antes del título). Empieza DIRECTAMENTE con `# Título` en la primera línea.
2. FIDELIDAD AL DOCUMENTO O NOTA FUENTE: Si hay un `DOCUMENTO` o `NOTA` adjunto/en sesión o contenido previo que el usuario pide pasar a una nota:
   - PROHIBIDO inventar o cambiar títulos (ej. 'Bitácora de Observación...'), inventar historias, resumir o alterar el contenido salvo que se te pida explícitamente. Transcribe y estructura FIELMENTE el texto real del documento original con exactitud.
   - Conserva el título real, la redacción, el orden, los diálogos, el espaciado, los estilos (`**negrita**`, `*cursiva*`, `<u>subrayado</u>`, colores) y la estructura original.
   - Si el usuario pide subtítulos desplegables o el documento tiene flechas ▼/▶, envuelve cada subtítulo con su contenido dentro de:
     <details><summary>Título del subtítulo</summary>

     Contenido completo de la sección...

     </details>
   - Preserva marcas literales como `Narrador**` y barras de cita `| ` o `> `.
3. No envuelvas la nota dentro de bloques de código (```markdown) ni añadas etiquetas JSON o técnicas.

$formattingContract
""".trimIndent()

            isSimpleQuery -> """
- Responde de forma directa, cálida y natural en español en 1 o 2 oraciones breves.
- No uses listas ni apartados analíticos para saludos o preguntas cotidianas simples.
""".trimIndent()

            isComplexQuery -> """
- Entrega directamente el análisis, resumen o respuesta completa en español con estructura clara (`#`, `##`, listas, tablas o `<details><summary>...</summary>` si ayuda).
- No respondas evadiendo ni pidiendo al usuario que aclare primero si ya tienes el tema o el documento adjunto.

$formattingContract
""".trimIndent()

            else -> """
- Responde en español natural, claro, ordenado y fiel a las instrucciones del usuario.

$formattingContract
""".trimIndent()
        }

        val engineGuidance = if (isGoogleAiStudio) {
            "- NO escribas etiquetas `<think>` ni `</think>` manualmente en tu respuesta; entrega directamente la respuesta o nota limpia en Markdown."
        } else {
            """
- Si usas razonamiento interno `<think>...</think>`, mantenlo breve (2-4 líneas en español) y ciérralo siempre con `</think>` antes de empezar tu respuesta.
- Nunca mezcles títulos `#` ni texto de la nota dentro de `<think>...</think>`.
""".trimIndent()
        }

        return """
Eres Aura, asistente inteligente de notas y productividad en UTK Notes. Tu idioma nativo es el español correcto y natural.
$engineGuidance
- Nunca incluyas etiquetas técnicas (`<assistant_answer>`, `<full_note>`, `<attached_file>`) ni bloques JSON crudos en la respuesta.

$taskSpecificRules
""".trimIndent().trim()
    }
}
