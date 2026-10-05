import sys

path = 'app/src/main/java/com/example/ui/AetherViewModel.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

target1 = """        val displayMsg = when {
            rawUserMsg.isNotBlank() -> rawUserMsg
            fName != null -> "📎 Archivo: $fName"
            imgUri != null -> "📷 [Imagen adjunta]"
            pNote != null -> "📄 [Nota citada: ${pNote.title}]"
            pText != null -> "📝 [Fragmento citado]"
            else -> "Consulta"
        }

        viewModelScope.launch {
            _isChatbotSending.value = true

            // Extract document text if URI is provided and content is not already extracted
            var actualFileContent = fContent
            if (fUri != null && actualFileContent.isNullOrBlank()) {
                _chatbotStatusText.value = "📄 Leyendo \\"${fName ?: "documento"}\\"..."
                actualFileContent = com.example.util.LocalMediaAnalyzer.extractDocumentText(context, fUri, fName)
            }"""

replacement1 = """        val displayMsg = when {
            rawUserMsg.isNotBlank() -> rawUserMsg
            fName != null -> "📎 Archivo: $fName"
            imgUri != null -> "📷 [Imagen adjunta]"
            pNote != null -> "📄 [Nota citada: ${pNote.title}]"
            pText != null -> "📝 [Fragmento citado]"
            else -> "Consulta"
        }

        // Add user message to chat immediately for instantaneous feedback
        _chatMessages.value = _chatMessages.value + (displayMsg to true)
        _isChatbotSending.value = true
        _chatbotStatusText.value = if (fUri != null || fName != null) "📄 Leyendo \\"${fName ?: "documento"}\\"..." else "🧠 Aura está pensando..."

        viewModelScope.launch {
            // Extract document text if URI is provided and content is not already extracted
            var actualFileContent = fContent
            if (fUri != null && actualFileContent.isNullOrBlank()) {
                _chatbotStatusText.value = "📄 Leyendo \\"${fName ?: "documento"}\\"..."
                actualFileContent = com.example.util.LocalMediaAnalyzer.extractDocumentText(context, fUri, fName)
            }"""

target2 = """            sendChatbotMessage(
                userDisplayMsg = displayMsg,
                promptWithContext = finalMsgForApi,
                imgB64 = imgB64,
                imgMime = imgMime
            )"""

replacement2 = """            sendChatbotMessage(
                userDisplayMsg = displayMsg,
                promptWithContext = finalMsgForApi,
                imgB64 = imgB64,
                imgMime = imgMime,
                alreadyAddedToChat = true
            )"""

target3 = """        // Safely limit user prompt message length to avoid overflowing context for local model
        val safeMessage = if (message.length > 5500) message.take(5500) + "\\n...[Contenido recortado]" else message

        val rawResponse = localLlm.generateResponse(
            prompt = safeMessage + imageDisclaimer,
            systemPrompt = finalSystemPrompt,
            history = history,
            onStatusUpdate = { status ->
                _chatbotStatusText.value = status
            }
        )"""

replacement3 = """        val isDocumentSummary = message.contains("Instrucción de resumen", ignoreCase = true) ||
                                message.contains("Archivo Adjunto", ignoreCase = true) ||
                                (message.contains("resum", ignoreCase = true) && message.contains("Contexto"))

        val effectiveSystemPrompt = if (isDocumentSummary) {
            _chatbotStatusText.value = "📄 Aura está analizando el documento..."
            \"\"\"
            Eres Aura, una IA asistente experta en toma de notas y análisis documental.
            Tu misión es leer el documento proporcionado y redactar un RESUMEN GLOBAL, coherente y unificado.
            REGLAS CRÍTICAS:
            1. Lee y analiza todo el texto en su conjunto. NUNCA hagas un desglose página por página ni enumeres páginas individuales.
            2. Presenta un resumen enriquecido usando Markdown:
               # [Título representativo del tema]
               ## Visión General
               (Un párrafo sólido resumiendo la tesis y propósito global del documento)
               ### Puntos Clave
               - **Concepto clave 1**: desarrollo claro y conciso
               - **Concepto clave 2**: desarrollo claro y conciso
               - **Concepto clave 3**: desarrollo claro y conciso
               ### Conclusiones y Aprendizajes
               (Síntesis final clara)
            3. Emplea negritas con **texto**, subtítulos con ## o ### y listas con viñetas '-' para una presentación visual limpia y estructurada.
            \"\"\".trimIndent()
        } else {
            finalSystemPrompt
        }

        // Safely limit user prompt message length to avoid overflowing context for local model
        val maxLen = if (isDocumentSummary) 3200 else 5500
        val safeMessage = if (message.length > maxLen) message.take(maxLen) + "\\n...[Contenido recortado para análisis global]" else message

        val rawResponse = localLlm.generateResponse(
            prompt = safeMessage + imageDisclaimer,
            systemPrompt = effectiveSystemPrompt,
            history = if (isDocumentSummary) emptyList() else history,
            onStatusUpdate = { status ->
                _chatbotStatusText.value = status
            }
        )"""

assert target1 in content, 'target1 not found'
assert target2 in content, 'target2 not found'
assert target3 in content, 'target3 not found'

content = content.replace(target1, replacement1, 1)
content = content.replace(target2, replacement2, 1)
content = content.replace(target3, replacement3, 1)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
print('Successfully updated AetherViewModel.kt')
