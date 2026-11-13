package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.BookEntity
import com.example.data.model.NoteEntity
import com.example.data.model.PageEntity
import com.example.data.remote.DriveSyncManager
import com.example.data.remote.SyncState
import com.example.data.repository.NotesRepository
import com.example.data.local.ai.AiApiSettingsState
import com.example.data.local.ai.AiStudioConfigStore
import com.example.data.local.ai.ApiKeyValidationState
import com.example.data.local.llm.LocalLlmManager
import com.example.data.local.llm.ModelDownloadVerifier
import com.example.data.local.llm.ModelDownloadStatus
import com.example.data.local.memory.NoteMemoryVaultManager
import com.example.data.remote.gemini.GoogleAiStudioManager
import com.example.data.remote.gemini.MissingApiKeyException
import com.example.ui.reminders.NotificationHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

data class AuraActionResponse(
    val action: String = "conversation",
    val targetScope: String = "chat",
    val explanation: String = "",
    val content: String = "",
    val thought: String = ""
)

enum class AiEngineType {
    GOOGLE_AI_STUDIO,
    AURA_LOCAL
}

class AetherViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = NotesRepository(database.notesDao())
    val syncManager = DriveSyncManager(application, database)
    val localLlm = LocalLlmManager(application)
    val modelVerifier = ModelDownloadVerifier(application)
    val memoryVault = NoteMemoryVaultManager(application)
    val aiStudioConfig = AiStudioConfigStore(application)

    private val _aiSettingsState = MutableStateFlow(aiStudioConfig.getSettingsSnapshot())
    val aiSettingsState: StateFlow<AiApiSettingsState> = _aiSettingsState.asStateFlow()

    val googleAiStudio = GoogleAiStudioManager(
        apiKeyProvider = { aiStudioConfig.getDecryptedApiKey() },
        modelProvider = { _aiSettingsState.value.selectedModel }
    )

    private val _selectedAiEngine = MutableStateFlow(aiStudioConfig.getSavedEngineType())
    val selectedAiEngine: StateFlow<AiEngineType> = _selectedAiEngine.asStateFlow()

    fun setAiEngine(engine: AiEngineType) {
        aiStudioConfig.saveEngineType(engine)
        _selectedAiEngine.value = engine
    }

    fun saveGoogleAiStudioApiKey(rawKey: String) {
        val currentValidation = ApiKeyValidationState.Idle
        _aiSettingsState.value = aiStudioConfig.saveApiKey(rawKey).copy(
            validationState = currentValidation
        )
    }

    fun clearGoogleAiStudioApiKey() {
        _aiSettingsState.value = aiStudioConfig.clearApiKey().copy(
            validationState = ApiKeyValidationState.Idle
        )
    }

    fun setGoogleAiStudioModel(modelId: String) {
        val currentValidation = _aiSettingsState.value.validationState
        _aiSettingsState.value = aiStudioConfig.saveSelectedModel(modelId).copy(
            validationState = currentValidation
        )
    }

    fun testGoogleAiStudioConnection(candidateApiKey: String? = null) {
        val trimmedCandidate = candidateApiKey?.trim()?.takeIf { it.isNotEmpty() }
        viewModelScope.launch {
            _aiSettingsState.value = _aiSettingsState.value.copy(
                validationState = ApiKeyValidationState.Validating
            )
            try {
                val models = googleAiStudio.listAvailableModels(apiKeyOverride = trimmedCandidate)
                if (trimmedCandidate != null) {
                    aiStudioConfig.saveApiKey(trimmedCandidate)
                }
                var updated = aiStudioConfig.saveDiscoveredModels(models, markVerified = true)
                if (models.isNotEmpty() && models.none { it.id.equals(updated.selectedModel, ignoreCase = true) }) {
                    val preferred = models.firstOrNull { it.id.equals(com.example.data.local.ai.DEFAULT_AI_STUDIO_MODEL, ignoreCase = true) }?.id
                        ?: models.first().id
                    updated = aiStudioConfig.saveSelectedModel(preferred)
                }
                _aiSettingsState.value = updated.copy(
                    validationState = ApiKeyValidationState.Valid(models.size),
                    isConnectionVerified = true
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val safeError = com.example.util.sanitizeSensitiveDetails(
                    e.localizedMessage.orEmpty(),
                    trimmedCandidate
                ).ifBlank { "No se pudo verificar la clave en Google AI Studio." }
                val updated = aiStudioConfig.setConnectionVerified(false)
                _aiSettingsState.value = updated.copy(
                    validationState = ApiKeyValidationState.Invalid(safeError),
                    isConnectionVerified = false
                )
            }
        }
    }

    fun refreshGoogleAiStudioModelsIfVerified() {
        val current = _aiSettingsState.value
        if (!current.hasApiKey || !current.isConnectionVerified) return
        if (current.validationState is ApiKeyValidationState.Validating) return

        viewModelScope.launch {
            try {
                val models = googleAiStudio.listAvailableModels(apiKeyOverride = null)
                var updated = aiStudioConfig.saveDiscoveredModels(models, markVerified = true)
                if (models.isNotEmpty() && models.none { it.id.equals(updated.selectedModel, ignoreCase = true) }) {
                    val preferred = models.firstOrNull { it.id.equals(com.example.data.local.ai.DEFAULT_AI_STUDIO_MODEL, ignoreCase = true) }?.id
                        ?: models.first().id
                    updated = aiStudioConfig.saveSelectedModel(preferred)
                }
                _aiSettingsState.value = updated.copy(
                    validationState = ApiKeyValidationState.Valid(models.size),
                    isConnectionVerified = true
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: com.example.data.remote.gemini.AiStudioApiException) {
                if (e.httpCode in listOf(400, 401, 403)) {
                    val updated = aiStudioConfig.setConnectionVerified(false)
                    _aiSettingsState.value = updated.copy(
                        validationState = ApiKeyValidationState.Invalid(e.message.orEmpty().ifBlank { "Clave API inválida." }),
                        isConnectionVerified = false
                    )
                }
            } catch (_: Exception) {
                // Error de red transitorio: mantener modelos cacheados y estado verificado
            }
        }
    }

    init {
        viewModelScope.launch {
            try {
                if (modelVerifier.checkCurrentStatus() is ModelDownloadStatus.Ready) {
                    val file = modelVerifier.modelFile
                    com.example.data.local.llm.GgufMetadataSanitizer.sanitizeChatTemplate(file)
                    localLlm.ensureModelLoaded(file, com.example.data.local.llm.LocalLlmManager.MIN_CONTEXT_SIZE)
                }
            } catch (e: Exception) {
                android.util.Log.e("AetherViewModel", "Error al inicializar modelo local", e)
            }
        }
    }

    fun selectAndLoadModel(model: com.example.data.local.llm.LlmModelItem) {
        viewModelScope.launch {
            modelVerifier.setActiveModel(model)
            if (modelVerifier.isModelDownloaded(model)) {
                localLlm.ensureModelLoaded(modelVerifier.getModelFile(model))
            }
        }
    }

    fun deleteModel(model: com.example.data.local.llm.LlmModelItem) {
        viewModelScope.launch {
            if (localLlm.modelState.value is com.example.data.local.llm.LlmModelState.Ready &&
                localLlm.activeModelPath == modelVerifier.getModelFile(model).absolutePath) {
                localLlm.unloadModel()
            }
            modelVerifier.deleteModelFile(model)
        }
    }

    fun downloadModel(model: com.example.data.local.llm.LlmModelItem) {
        viewModelScope.launch {
            modelVerifier.startDownload(model)
            if (modelVerifier.isModelDownloaded(model) && modelVerifier.activeModel.value.id == model.id) {
                localLlm.ensureModelLoaded(modelVerifier.getModelFile(model))
            }
        }
    }


    // --- SELECTION STATES ---
    private val _selectedBook = MutableStateFlow<BookEntity?>(null)
    val selectedBook: StateFlow<BookEntity?> = _selectedBook.asStateFlow()

    private val _selectedPage = MutableStateFlow<PageEntity?>(null)
    val selectedPage: StateFlow<PageEntity?> = _selectedPage.asStateFlow()

    private val _selectedNote = MutableStateFlow<NoteEntity?>(null)
    val selectedNote: StateFlow<NoteEntity?> = _selectedNote.asStateFlow()

    // --- SEARCH STATE ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // --- AI STATES ---
    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading.asStateFlow()

    private val _aiSuggestions = MutableStateFlow<List<String>>(emptyList())
    val aiSuggestions: StateFlow<List<String>> = _aiSuggestions.asStateFlow()

    private val _aiMessage = MutableStateFlow<String?>(null)
    val aiMessage: StateFlow<String?> = _aiMessage.asStateFlow()

    // --- CHATBOT TOP-LEVEL STATES ---
    private val _showChatbot = MutableStateFlow(false)
    val showChatbot: StateFlow<Boolean> = _showChatbot.asStateFlow()

    private val _chatbotPreAttachedNote = MutableStateFlow<NoteEntity?>(null)
    val chatbotPreAttachedNote: StateFlow<NoteEntity?> = _chatbotPreAttachedNote.asStateFlow()

    private val _chatbotPreAttachedText = MutableStateFlow<String?>(null)
    val chatbotPreAttachedText: StateFlow<String?> = _chatbotPreAttachedText.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<Pair<String, Boolean>>>(emptyList())
    val chatMessages: StateFlow<List<Pair<String, Boolean>>> = _chatMessages.asStateFlow()

    private val _currentChatSessionId = MutableStateFlow<String?>(null)
    val currentChatSessionId: StateFlow<String?> = _currentChatSessionId.asStateFlow()

    private val _isChatbotSending = MutableStateFlow(false)
    val isChatbotSending: StateFlow<Boolean> = _isChatbotSending.asStateFlow()

    private val _chatbotStatusText = MutableStateFlow<String?>(null)
    val chatbotStatusText: StateFlow<String?> = _chatbotStatusText.asStateFlow()

    private val _liveThinkingText = MutableStateFlow<String?>(null)
    val liveThinkingText: StateFlow<String?> = _liveThinkingText.asStateFlow()

    // --- DOCUMENT IMPORT PROGRESS STATES (GOOGLE ML KIT) ---
    private val _isImportingDocument = MutableStateFlow(false)
    val isImportingDocument: StateFlow<Boolean> = _isImportingDocument.asStateFlow()

    private val _importProgress = MutableStateFlow<Pair<Int, Int>?>(null) // (current, total)
    val importProgress: StateFlow<Pair<Int, Int>?> = _importProgress.asStateFlow()

    private val _importStatusMessage = MutableStateFlow<String?>(null)
    val importStatusMessage: StateFlow<String?> = _importStatusMessage.asStateFlow()

    fun setImportProgress(current: Int, total: Int, message: String) {
        _isImportingDocument.value = true
        _importProgress.value = Pair(current, total)
        _importStatusMessage.value = message
    }

    fun clearImportProgress() {
        _isImportingDocument.value = false
        _importProgress.value = null
        _importStatusMessage.value = null
    }

    private var chatbotJob: kotlinx.coroutines.Job? = null

    fun openChatbot(preAttachedNote: NoteEntity? = null, preAttachedText: String? = null) {
        if (preAttachedNote != null) _chatbotPreAttachedNote.value = preAttachedNote
        if (preAttachedText != null) _chatbotPreAttachedText.value = preAttachedText
        _showChatbot.value = true
    }

    fun closeChatbot() {
        _showChatbot.value = false
        // DO NOT wipe _chatMessages so the user never loses the current conversation when sliding down!
    }

    data class SessionSourceContext(
        val title: String,
        val content: String,
        val isNote: Boolean = false
    )

    private val sessionSourceMap = mutableMapOf<String, SessionSourceContext>()

    private fun ensureCurrentSessionId(): String {
        val current = _currentChatSessionId.value
        if (current != null) return current
        val generated = UUID.randomUUID().toString()
        _currentChatSessionId.value = generated
        return generated
    }

    private fun serializeChatMessagesWithMeta(
        list: List<Pair<String, Boolean>>,
        sourceCtx: SessionSourceContext?
    ): String {
        val array = org.json.JSONArray()
        val lastUserIdx = list.indexOfLast { it.second }
        list.forEachIndexed { idx, pair ->
            val obj = org.json.JSONObject()
            obj.put("text", pair.first)
            obj.put("isUser", pair.second)
            if (idx == lastUserIdx && sourceCtx != null && sourceCtx.content.isNotBlank()) {
                obj.put("sessionSourceTitle", sourceCtx.title)
                obj.put("sessionSourceContent", sourceCtx.content.take(40_000))
                obj.put("sessionSourceIsNote", sourceCtx.isNote)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun extractSessionSourceFromJson(json: String): SessionSourceContext? {
        return try {
            val array = org.json.JSONArray(json)
            for (i in (array.length() - 1) downTo 0) {
                val obj = array.optJSONObject(i) ?: continue
                val content = obj.optString("sessionSourceContent", "")
                if (content.isNotBlank()) {
                    return SessionSourceContext(
                        title = obj.optString("sessionSourceTitle", "Documento"),
                        content = content,
                        isNote = obj.optBoolean("sessionSourceIsNote", false)
                    )
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun setChatSession(sessionId: String?, messages: List<Pair<String, Boolean>>) {
        chatbotJob?.cancel()
        chatbotJob = null
        localLlm.stopGeneration()
        _currentChatSessionId.value = sessionId
        _chatMessages.value = messages
        _isChatbotSending.value = false
        _chatbotStatusText.value = null
        _liveThinkingText.value = null
        if (sessionId != null && !sessionSourceMap.containsKey(sessionId)) {
            viewModelScope.launch(Dispatchers.IO) {
                val session = repository.getChatSession(sessionId)
                val recovered = session?.let { extractSessionSourceFromJson(it.messagesJson) }
                if (recovered != null) {
                    sessionSourceMap[sessionId] = recovered
                }
            }
        }
    }

    fun clearChat() {
        chatbotJob?.cancel()
        chatbotJob = null
        localLlm.stopGeneration()
        _currentChatSessionId.value = null
        _chatMessages.value = emptyList()
        _chatbotPreAttachedNote.value = null
        _chatbotPreAttachedText.value = null
        _isChatbotSending.value = false
        _chatbotStatusText.value = null
        _liveThinkingText.value = null
    }

    fun sendChatbotMessage(
        userDisplayMsg: String,
        promptWithContext: String,
        imgB64: String? = null,
        imgMime: String? = null,
        alreadyAddedToChat: Boolean = false,
        rawUserPrompt: String = userDisplayMsg
    ) {
        val updatedList = if (alreadyAddedToChat) _chatMessages.value else (_chatMessages.value + (userDisplayMsg to true))
        val userIndex = updatedList.indexOfLast { it.second }.coerceAtLeast(0)
        executeChatbotTurn(
            baseMessages = updatedList,
            insertResponseAt = updatedList.size,
            historyMessages = if (userIndex > 0) updatedList.take(userIndex) else emptyList(),
            sessionTitleCandidate = userDisplayMsg,
            promptWithContext = promptWithContext,
            imgB64 = imgB64,
            imgMime = imgMime,
            rawUserPrompt = rawUserPrompt
        )
    }

    private fun executeChatbotTurn(
        baseMessages: List<Pair<String, Boolean>>,
        insertResponseAt: Int,
        historyMessages: List<Pair<String, Boolean>>,
        sessionTitleCandidate: String,
        promptWithContext: String,
        imgB64: String? = null,
        imgMime: String? = null,
        rawUserPrompt: String
    ) {
        _chatMessages.value = baseMessages
        _isChatbotSending.value = true
        _liveThinkingText.value = null
        if (_chatbotStatusText.value.isNullOrBlank()) {
            _chatbotStatusText.value = "Aura está pensando..."
        }

        val sId = ensureCurrentSessionId()
        val isGeminiEngine = _selectedAiEngine.value == AiEngineType.GOOGLE_AI_STUDIO
        val historyTurnCap = if (isGeminiEngine) 8 else 4
        val historyCharCap = if (isGeminiEngine) 4_000 else 800

        chatbotJob?.cancel()
        chatbotJob = viewModelScope.launch {
            try {
                val existing = repository.getChatSession(sId)
                val chatTitle = existing?.title?.takeIf { it.isNotBlank() }
                    ?: sessionTitleCandidate.take(35).ifBlank { "Conversación" }
                saveChatSessionSync(
                    sId,
                    chatTitle,
                    serializeChatMessagesWithMeta(baseMessages, sessionSourceMap[sId])
                )

                val recentHistory = historyMessages.takeLast(historyTurnCap).map { (text, isUser) ->
                    val cleanedTurn = if (isUser) {
                        text
                    } else {
                        val parsed = parseAuraJsonResponse(text)
                        parsed.content.ifBlank { cleanRawJsonForDisplay(text) }
                    }
                    (if (isUser) "user" else "assistant") to cleanedTurn.take(historyCharCap)
                }
                val response = sendMessage(
                    message = promptWithContext,
                    imageBase64 = imgB64,
                    mimeType = imgMime,
                    history = recentHistory,
                    rawUserPrompt = rawUserPrompt
                )
                val finalResult = response ?: "Error al obtener respuesta de Aura"

                val finalMessages = baseMessages.toMutableList().apply {
                    val safeIdx = insertResponseAt.coerceIn(0, size)
                    add(safeIdx, finalResult to false)
                }
                saveChatSessionSync(
                    sId,
                    chatTitle,
                    serializeChatMessagesWithMeta(finalMessages, sessionSourceMap[sId])
                )

                if (_currentChatSessionId.value == sId) {
                    _chatMessages.value = finalMessages
                    _isChatbotSending.value = false
                    _chatbotStatusText.value = null
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (_currentChatSessionId.value == sId) {
                    _isChatbotSending.value = false
                    _chatbotStatusText.value = null
                }
            } catch (e: Exception) {
                if (_currentChatSessionId.value == sId) {
                    val finalMessages = baseMessages.toMutableList().apply {
                        val safeIdx = insertResponseAt.coerceIn(0, size)
                        add(safeIdx, "Error en la respuesta: ${e.message}" to false)
                    }
                    _chatMessages.value = finalMessages
                    _isChatbotSending.value = false
                    _chatbotStatusText.value = null
                }
            } finally {
                if (_currentChatSessionId.value == sId) {
                    _isChatbotSending.value = false
                    _chatbotStatusText.value = null
                }
            }
        }
    }

    private fun buildTurnPromptWithContext(
        userMsg: String,
        allNotes: List<NoteEntity>,
        extraShortcutContext: String = ""
    ): String {
        val sId = _currentChatSessionId.value
        val sessionSource = if (sId != null) sessionSourceMap[sId] else null
        val isGeminiEngine = _selectedAiEngine.value == AiEngineType.GOOGLE_AI_STUDIO
        val contextLimit = if (isGeminiEngine) 35_000 else 4_500

        val isGreeting = isGreetingOrGeneralQuery(userMsg) && extraShortcutContext.isBlank()
        val isIndependent = com.example.util.ChatNotePipeline.isIndependentGeneralTopic(userMsg)
        val mentionedNote = resolveMentionedNote(userMsg, allNotes)
        val refsActive = com.example.util.ChatNotePipeline.detectActiveNoteReference(userMsg)

        val sb = StringBuilder()
        if (extraShortcutContext.isNotBlank()) {
            sb.append(extraShortcutContext.trim()).append("\n\n")
        }
        when {
            mentionedNote != null -> {
                val formatted = formatNoteBlocksForLlm(mentionedNote.content, maxChars = contextLimit)
                sb.append("=== NOTA MENCIONADA (\"${mentionedNote.title}\") ===\n")
                sb.append(formatted)
                sb.append("\n=========================================\n\n")
                sb.append("Instrucción del usuario: ").append(userMsg)
            }
            sessionSource != null && !isGreeting && !isIndependent -> {
                val header = if (sessionSource.isNote) {
                    "=== NOTA DE REFERENCIA DE LA SESIÓN (\"${sessionSource.title}\") ==="
                } else {
                    "=== DOCUMENTO DE REFERENCIA DE LA SESIÓN (\"${sessionSource.title}\") ==="
                }
                val safeSrc = if (sessionSource.content.length > contextLimit) {
                    sessionSource.content.take(contextLimit) + "\n...[Documento extenso condensado]"
                } else sessionSource.content
                sb.append(header).append("\n").append(safeSrc)
                sb.append("\n===========================================\n\n")
                sb.append("Instrucción del usuario: ").append(userMsg)
            }
            refsActive && _selectedNote.value != null -> {
                val active = _selectedNote.value!!
                val formatted = formatNoteBlocksForLlm(active.content, maxChars = contextLimit)
                sb.append("=== NOTA ACTIVA (\"${active.title}\") ===\n")
                sb.append(formatted)
                sb.append("\n=========================================\n\n")
                sb.append("Instrucción del usuario: ").append(userMsg)
            }
            extraShortcutContext.isNotBlank() -> {
                sb.append("Instrucción del usuario: ").append(userMsg)
            }
            else -> sb.append(userMsg)
        }
        return sb.toString()
    }

    fun regenerateResponseAt(assistantIndex: Int, allNotes: List<NoteEntity>) {
        val currentMsgs = _chatMessages.value
        if (assistantIndex !in currentMsgs.indices || currentMsgs[assistantIndex].second) {
            regenerateLastChatbotResponse(allNotes)
            return
        }
        var userIdx = -1
        for (i in (assistantIndex - 1) downTo 0) {
            if (currentMsgs[i].second) {
                userIdx = i
                break
            }
        }
        if (userIdx == -1) return

        val userMsg = currentMsgs[userIdx].first
        val (cleanedMessages, insertSlot) = removeAssistantTurnResponses(currentMsgs, userIdx)
        val promptWithContext = buildTurnPromptWithContext(userMsg, allNotes)

        executeChatbotTurn(
            baseMessages = cleanedMessages,
            insertResponseAt = insertSlot,
            historyMessages = cleanedMessages.take(userIdx),
            sessionTitleCandidate = userMsg,
            promptWithContext = promptWithContext,
            rawUserPrompt = userMsg
        )
    }

    fun editUserMessageAndRegenerate(
        userIndex: Int,
        newUserDisplayMsg: String,
        extraShortcutContext: String = "",
        allNotes: List<NoteEntity>
    ) {
        val trimmedNewMsg = newUserDisplayMsg.trim()
        if (trimmedNewMsg.isEmpty()) return
        val currentMsgs = _chatMessages.value.toMutableList()
        if (userIndex !in currentMsgs.indices || !currentMsgs[userIndex].second) return

        currentMsgs[userIndex] = trimmedNewMsg to true
        val (cleanedMessages, insertSlot) = removeAssistantTurnResponses(currentMsgs, userIndex)
        val promptWithContext = buildTurnPromptWithContext(trimmedNewMsg, allNotes, extraShortcutContext)

        executeChatbotTurn(
            baseMessages = cleanedMessages,
            insertResponseAt = insertSlot,
            historyMessages = cleanedMessages.take(userIndex),
            sessionTitleCandidate = trimmedNewMsg,
            promptWithContext = promptWithContext,
            rawUserPrompt = trimmedNewMsg
        )
    }

    fun regenerateLastChatbotResponse(allNotes: List<NoteEntity>) {
        val currentMsgs = _chatMessages.value
        val lastUserIdx = currentMsgs.indexOfLast { it.second }
        if (lastUserIdx == -1) return
        val lastUserMsg = currentMsgs[lastUserIdx].first
        val (cleanedMessages, insertSlot) = removeAssistantTurnResponses(currentMsgs, lastUserIdx)
        val promptWithContext = buildTurnPromptWithContext(lastUserMsg, allNotes)

        executeChatbotTurn(
            baseMessages = cleanedMessages,
            insertResponseAt = insertSlot,
            historyMessages = cleanedMessages.take(lastUserIdx),
            sessionTitleCandidate = lastUserMsg,
            promptWithContext = promptWithContext,
            rawUserPrompt = lastUserMsg
        )
    }

    /**
     * Exporta instantáneamente cualquier mensaje del chatbot a una nota en la base de datos local,
     * limpiando bloques <think>, etiquetas técnicas y marcadores internos, y preservando formato o bloques.
     */
    fun exportMessageToNote(
        msgContent: String,
        asBlocks: Boolean,
        customTitle: String? = null,
        onSuccess: (NoteEntity) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val targetPage = ensureDefaultTargetPage()

                val parsedAction = parseAuraJsonResponse(msgContent)
                val cleanBody = parsedAction.content
                    .ifBlank { parsedAction.explanation }
                    .ifBlank { cleanRawJsonForDisplay(msgContent) }
                    .let { com.example.util.ChatNotePipeline.stripNoteCreatedMarker(it).first }
                    .trim()

                val extractedTitle = com.example.util.ChatNotePipeline.extractTitleFromNoteMarkdown(
                    cleanBody,
                    fallback = if (asBlocks) "Nota Estructurada de Aura" else "Nota de Aura"
                )
                val title = customTitle?.trim()?.takeIf { it.isNotBlank() } ?: extractedTitle

                val rawBlocks = com.example.parseTextContentToBlocks(cleanBody, splitLinesAsBlocks = asBlocks)
                val parsedBlocks = com.example.stripLeadingDuplicateTitleBlock(rawBlocks, title)
                    .ifEmpty { rawBlocks }
                    .ifEmpty { listOf(com.example.EditorBlock.Text(content = cleanBody)) }
                val serializedContent = com.example.serializeBlocks(parsedBlocks)

                val note = repository.createNote(
                    pageId = targetPage.id,
                    title = title,
                    content = serializedContent,
                    tags = if (asBlocks) "Bloques, Aura" else "Aura, Nota",
                    attachments = "[]",
                    reminderTime = null,
                    userEmail = currentEmail.value
                )
                _selectedNote.value = note
                if (syncManager.isConnected.value) {
                    triggerDriveSync()
                }
                withContext(Dispatchers.Main) {
                    onSuccess(note)
                }
            } catch (e: Exception) {
                android.util.Log.e("AetherViewModel", "Error al exportar mensaje a nota", e)
            }
        }
    }

    private suspend fun ensureDefaultTargetPage(): PageEntity {
        val current = _selectedPage.value
        if (current != null) return current
        var targetBook = _selectedBook.value
        if (targetBook == null) {
            val allBooks = repository.getAllBooksList(currentEmail.value)
            targetBook = allBooks.firstOrNull() ?: repository.createBook("Mis Notas", userEmail = currentEmail.value)
            _selectedBook.value = targetBook
        }
        val pages = repository.getPagesForBookList(targetBook.id)
        val targetPage = pages.firstOrNull() ?: repository.createPage(targetBook.id, "General", userEmail = currentEmail.value)
        _selectedPage.value = targetPage
        return targetPage
    }

    private fun resolveMentionedNote(rawUserMsg: String, allNotes: List<NoteEntity>): NoteEntity? {
        if (allNotes.isEmpty() || !rawUserMsg.contains("@")) return null
        val candidates = com.example.util.ChatNotePipeline.resolveMentionedNoteTitle(rawUserMsg)
        for (candidate in candidates) {
            val exact = allNotes.firstOrNull { it.title.equals(candidate, ignoreCase = true) }
            if (exact != null) return exact
            val prefix = allNotes.firstOrNull { it.title.startsWith(candidate, ignoreCase = true) }
            if (prefix != null) return prefix
            val contains = allNotes.firstOrNull {
                candidate.length >= 3 && it.title.contains(candidate, ignoreCase = true)
            }
            if (contains != null) return contains
        }
        return null
    }

    private fun isGreetingOrGeneralQuery(msg: String): Boolean {
        val clean = msg.trim().lowercase().replace(Regex("[!?,.¡¿]"), "").trim()
        if (clean.isEmpty()) return true
        val greetings = setOf(
            "hola", "hola aura", "hola buenas", "buenas", "buenos dias", "buenos días", "buenas tardes", "buenas noches",
            "hey", "hi", "hello", "que tal", "qué tal", "como estas", "cómo estás", "que haces", "qué haces",
            "saludos", "ayuda", "que puedes hacer", "qué puedes hacer", "quien eres", "quién eres"
        )
        if (clean in greetings) return true
        if (clean.matches(Regex("^(hola|buenas|saludos|hey|hi)(\\s+(aura|amigo|asistente|que tal|como estas))?$"))) return true
        if (clean.startsWith("hola") && clean.length <= 15) return true
        return false
    }

    /**
     * Envía un mensaje del usuario asegurando ejecución en segundo plano (viewModelScope),
     * preservando el contexto completo del documento o nota de la sesión entre múltiples turnos
     * y resolviendo menciones @nota.
     */
    fun sendUserMessageWithAttachments(
        context: Context,
        rawUserMsg: String,
        fName: String?,
        fContent: String?,
        fUri: Uri? = null,
        imgUri: Uri?,
        imgB64: String?,
        imgMime: String?,
        pNote: NoteEntity?,
        pText: String?,
        allNotes: List<NoteEntity>,
        extraShortcutContext: String = ""
    ) {
        // Consumir pre-adjuntos pendientes para no filtrarlos en mensajes posteriores
        _chatbotPreAttachedNote.value = null
        _chatbotPreAttachedText.value = null

        val displayMsg = when {
            rawUserMsg.isNotBlank() -> rawUserMsg
            fName != null -> "📎 Archivo: $fName"
            imgUri != null -> "📷 [Imagen adjunta]"
            pNote != null -> "📄 [Nota citada: ${pNote.title}]"
            pText != null -> "📝 [Fragmento citado]"
            else -> "Consulta"
        }

        val sId = ensureCurrentSessionId()
        _chatMessages.value = _chatMessages.value + (displayMsg to true)
        _isChatbotSending.value = true
        _liveThinkingText.value = null
        _chatbotStatusText.value = if (fUri != null || fName != null) "Leyendo \"${fName ?: "documento"}\"..." else "Aura está pensando..."

        viewModelScope.launch {
            var actualFileContent = fContent
            if (fUri != null && actualFileContent.isNullOrBlank()) {
                _chatbotStatusText.value = "Leyendo \"${fName ?: "documento"}\"..."
                actualFileContent = com.example.util.LocalMediaAnalyzer.extractDocumentText(context, fUri, fName)
            }

            var imageOcrText = ""
            if (imgUri != null) {
                _chatbotStatusText.value = "Analizando imagen con OCR..."
                imageOcrText = com.example.util.LocalMediaAnalyzer.analyzeImage(context, imgUri)
            }

            val isGeminiEngine = _selectedAiEngine.value == AiEngineType.GOOGLE_AI_STUDIO
            val docLimit = if (isGeminiEngine) 35_000 else 4_500
            val noteLimit = if (isGeminiEngine) 35_000 else 3_000

            val isGreeting = isGreetingOrGeneralQuery(rawUserMsg) &&
                actualFileContent.isNullOrBlank() && imgUri == null && pNote == null && pText.isNullOrBlank() && extraShortcutContext.isBlank()
            val isIndependent = com.example.util.ChatNotePipeline.isIndependentGeneralTopic(rawUserMsg)
            val refsActiveNote = com.example.util.ChatNotePipeline.detectActiveNoteReference(rawUserMsg)
            val mentionedNote = resolveMentionedNote(rawUserMsg, allNotes)

            val explicitTargetNote: NoteEntity? = when {
                pNote != null -> pNote
                mentionedNote != null -> mentionedNote
                refsActiveNote -> _selectedNote.value
                else -> null
            }

            // Actualizar la memoria fuente de esta sesión si llega un nuevo documento o nota citada
            if (!actualFileContent.isNullOrBlank()) {
                sessionSourceMap[sId] = SessionSourceContext(
                    title = fName ?: "documento",
                    content = actualFileContent.take(45_000),
                    isNote = false
                )
            } else if (explicitTargetNote != null) {
                val fullNoteMd = formatNoteBlocksForLlm(explicitTargetNote.content, maxChars = 45_000)
                sessionSourceMap[sId] = SessionSourceContext(
                    title = explicitTargetNote.title,
                    content = fullNoteMd,
                    isNote = true
                )
            }

            val storedSessionSource = if (
                actualFileContent.isNullOrBlank() &&
                explicitTargetNote == null &&
                imgUri == null &&
                pText.isNullOrBlank() &&
                extraShortcutContext.isBlank() &&
                !isGreeting &&
                !isIndependent
            ) {
                sessionSourceMap[sId]
            } else {
                null
            }

            val sb = StringBuilder()
            val hasAnyStructuredContext = !actualFileContent.isNullOrBlank() ||
                imageOcrText.isNotBlank() ||
                explicitTargetNote != null ||
                !pText.isNullOrBlank() ||
                storedSessionSource != null ||
                extraShortcutContext.isNotBlank()

            if (isGreeting || !hasAnyStructuredContext) {
                sb.append(rawUserMsg)
            } else {
                if (extraShortcutContext.isNotBlank()) {
                    sb.append(extraShortcutContext.trim()).append("\n\n")
                }

                if (!actualFileContent.isNullOrBlank()) {
                    val safeFile = if (actualFileContent.length > docLimit) {
                        actualFileContent.take(docLimit) + "\n...[Documento extenso condensado]"
                    } else actualFileContent
                    sb.append("=== DOCUMENTO ADJUNTO (\"${fName ?: "archivo"}\") ===\n")
                    sb.append(safeFile)
                    sb.append("\n===========================================\n\n")
                }

                if (imageOcrText.isNotBlank()) {
                    sb.append("=== TEXTO DETECTADO EN IMAGEN (OCR) ===\n")
                    sb.append(imageOcrText)
                    sb.append("\n======================================\n\n")
                }

                if (explicitTargetNote != null) {
                    val formattedNote = formatNoteBlocksForLlm(explicitTargetNote.content, maxChars = noteLimit)
                    val label = if (mentionedNote != null) "NOTA MENCIONADA" else "NOTA ACTIVA"
                    sb.append("=== $label (\"${explicitTargetNote.title}\") ===\n")
                    sb.append(formattedNote)
                    sb.append("\n=========================================\n\n")
                } else if (storedSessionSource != null) {
                    val label = if (storedSessionSource.isNote) "NOTA DE REFERENCIA DE LA SESIÓN" else "DOCUMENTO DE REFERENCIA DE LA SESIÓN"
                    val safeStored = if (storedSessionSource.content.length > docLimit) {
                        storedSessionSource.content.take(docLimit) + "\n...[Documento extenso condensado]"
                    } else storedSessionSource.content
                    sb.append("=== $label (\"${storedSessionSource.title}\") ===\n")
                    sb.append(safeStored)
                    sb.append("\n===========================================\n\n")
                }

                if (!pText.isNullOrBlank()) {
                    sb.append("=== FRAGMENTO SELECCIONADO ===\n")
                    sb.append(pText.take(4_000))
                    sb.append("\n==============================\n\n")
                }

                val promptText = when {
                    rawUserMsg.isNotBlank() -> rawUserMsg
                    !actualFileContent.isNullOrBlank() -> "Por favor analiza el documento adjunto y presenta un resumen completo y estructurado de sus ideas clave en español."
                    imgUri != null -> "Analiza la imagen adjunta, describe sus elementos visuales y resume su texto en español."
                    else -> "Resume el contenido de forma clara y estructurada en español."
                }

                sb.append("Instrucción del usuario: ").append(promptText)
            }

            val finalMsgForApi = sb.toString()

            sendChatbotMessage(
                userDisplayMsg = displayMsg,
                promptWithContext = finalMsgForApi,
                imgB64 = imgB64,
                imgMime = imgMime,
                alreadyAddedToChat = true,
                rawUserPrompt = rawUserMsg.ifBlank { displayMsg }
            )
        }
    }

    private fun deserializeChatMessages(json: String): List<Pair<String, Boolean>> {
        return try {
            val array = org.json.JSONArray(json)
            val list = mutableListOf<Pair<String, Boolean>>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(obj.getString("text") to obj.getBoolean("isUser"))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    val currentEmail: StateFlow<String> = syncManager.userEmail
        .map { it ?: "offline" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "offline")

    // --- DATABASE LISTS FLOWS ---
    val books: StateFlow<List<BookEntity>> = currentEmail
        .flatMapLatest { email -> repository.getBooksForUser(email) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pages: StateFlow<List<PageEntity>> = _selectedBook
        .flatMapLatest { book ->
            if (book != null) repository.getPagesForBook(book.id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<NoteEntity>> = _selectedPage
        .flatMapLatest { page ->
            if (page != null) repository.getNotesForPage(page.id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search Results Flow
    val searchResults: StateFlow<List<NoteEntity>> = combine(_searchQuery.debounce(300), currentEmail) { query, email ->
        query to email
    }
    .flatMapLatest { (query, email) ->
        if (query.isNotEmpty()) repository.searchNotes(query, email)
        else flowOf(emptyList())
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active pending reminders
    val pendingReminders: StateFlow<List<NoteEntity>> = currentEmail
        .flatMapLatest { email -> repository.getPendingReminders(email) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All active notes in the app (for mention lookup)
    val allNotes: StateFlow<List<NoteEntity>> = currentEmail
        .flatMapLatest { email -> repository.searchNotes("", email) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPages: StateFlow<List<PageEntity>> = currentEmail
        .flatMapLatest { email -> repository.getAllPagesForUser(email) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All chat history sessions
    val allChatSessions: StateFlow<List<com.example.data.model.ChatSessionEntity>> = currentEmail
        .flatMapLatest { email -> repository.getChatSessions(email) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Deleted notes in trash
    val deletedNotes: StateFlow<List<NoteEntity>> = currentEmail
        .flatMapLatest { email -> repository.getDeletedNotes(email) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Deleted books in trash
    val deletedBooks: StateFlow<List<BookEntity>> = currentEmail
        .flatMapLatest { email -> repository.getDeletedBooks(email) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private suspend fun saveChatSessionSync(id: String, title: String, messagesJson: String) {
        repository.insertChatSession(
            com.example.data.model.ChatSessionEntity(
                id = id,
                title = title,
                messagesJson = messagesJson,
                createdAt = System.currentTimeMillis(),
                userEmail = currentEmail.value
            )
        )
    }

    fun saveChatSession(id: String, title: String, messagesJson: String) {
        viewModelScope.launch {
            saveChatSessionSync(id, title, messagesJson)
        }
    }

    fun deleteChatSession(session: com.example.data.model.ChatSessionEntity) {
        viewModelScope.launch {
            repository.deleteChatSession(session)
        }
    }

    init {
        // Create Notification Channel for reminders
        NotificationHelper.createNotificationChannel(application)

        // Clear select state on account changes to avoid data leak
        viewModelScope.launch {
            currentEmail.collect { email ->
                _selectedBook.value = null
                _selectedPage.value = null
                _selectedNote.value = null
            }
        }

        // Start reminder poll trigger
        startReminderPoll()
    }

    private suspend fun seedInitialData() {
        // Left empty intentionally so that the initial library is completely blank
        _selectedBook.value = null
        _selectedPage.value = null
    }

    private fun startReminderPoll() {
        // Periodically checks if any pending reminder is due and pushes notification in real-time
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10000) // check every 10 seconds
                val now = System.currentTimeMillis()
                val pending = repository.getPendingRemindersList(currentEmail.value)
                for (note in pending) {
                    val time = note.reminderTime ?: continue
                    if (now >= time) {
                        // Push system notification
                        NotificationHelper.sendReminderNotification(
                            getApplication(),
                            note.id,
                            note.title,
                            note.content
                        )
                        // Update status to completed so we don't trigger again
                        repository.completeReminder(note.id)
                    }
                }
            }
        }
    }

    // --- BOOK OPERATIONS ---
    fun clearSelectedBook() { _selectedBook.value = null; _selectedPage.value = null; _selectedNote.value = null }
    fun clearSelectedPage() { _selectedPage.value = null; _selectedNote.value = null }
    fun selectBook(book: BookEntity) {
        android.util.Log.d("AetherViewModel", "Selecting book: ${book.title}")
        _selectedBook.value = book
        _selectedNote.value = null
        viewModelScope.launch {
            val bookPages = repository.getPagesForBookList(book.id)
            android.util.Log.d("AetherViewModel", "Fetched pages: ${bookPages.size}")
            if (bookPages.isEmpty()) {
                val newPage = repository.createPage(book.id, "Notas", userEmail = currentEmail.value)
                _selectedPage.value = newPage
            } else {
                _selectedPage.value = bookPages.firstOrNull()
            }
        }
    }

    fun addBook(
        title: String,
        colorHex: String = "#907CFF",
        textColorHex: String = "#FFFFFF",
        coverUri: String? = null,
        coverScale: Float = 1.0f,
        coverOffsetX: Float = 0.0f,
        coverOffsetY: Float = 0.0f,
        fontFamily: String = "Sans"
    ) {
        android.util.Log.d("AetherViewModel", "Adding book: $title")
        viewModelScope.launch {
            val book = repository.createBook(
                title = title,
                colorHex = colorHex,
                textColorHex = textColorHex,
                coverUri = coverUri,
                coverScale = coverScale,
                coverOffsetX = coverOffsetX,
                coverOffsetY = coverOffsetY,
                fontFamily = fontFamily,
                userEmail = currentEmail.value
            )
            android.util.Log.d("AetherViewModel", "Book added: ${book.id}")
            _selectedBook.value = book
            val page = repository.createPage(book.id, "Notas", userEmail = currentEmail.value)
            _selectedPage.value = page
            _selectedNote.value = null
        }
    }

    fun updateBook(book: BookEntity) {
        viewModelScope.launch {
            repository.updateBook(book)
            if (_selectedBook.value?.id == book.id) {
                _selectedBook.value = book
            }
        }
    }

    fun renameBook(book: BookEntity, newTitle: String) {
        viewModelScope.launch {
            repository.updateBook(book.copy(title = newTitle))
            if (_selectedBook.value?.id == book.id) {
                _selectedBook.value = _selectedBook.value?.copy(title = newTitle)
            }
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch {
            repository.deleteBookSoft(book.id)
            if (_selectedBook.value?.id == book.id) {
                _selectedBook.value = null
                _selectedPage.value = null
                _selectedNote.value = null
            }
        }
    }

    fun restoreBook(bookId: String) {
        viewModelScope.launch {
            repository.restoreBook(bookId)
        }
    }

    fun deleteBookPermanently(book: BookEntity) {
        viewModelScope.launch {
            repository.deleteBookPermanent(book)
        }
    }


    // --- PAGE OPERATIONS ---
    fun selectPage(page: PageEntity) {
        _selectedPage.value = page
        _selectedNote.value = null
    }

    fun addPage(title: String) {
        val currentBook = _selectedBook.value ?: return
        viewModelScope.launch {
            val page = repository.createPage(currentBook.id, title, userEmail = currentEmail.value)
            _selectedPage.value = page
            _selectedNote.value = null
        }
    }

    fun renamePage(page: PageEntity, newTitle: String) {
        viewModelScope.launch {
            repository.updatePage(page.copy(title = newTitle))
            if (_selectedPage.value?.id == page.id) {
                _selectedPage.value = _selectedPage.value?.copy(title = newTitle)
            }
        }
    }

    fun deletePage(page: PageEntity) {
        val currentBook = _selectedBook.value ?: return
        viewModelScope.launch {
            repository.deletePage(page)
            if (_selectedPage.value?.id == page.id) {
                val remaining = repository.getPagesForBookList(currentBook.id)
                _selectedPage.value = remaining.firstOrNull()
                _selectedNote.value = null
            }
        }
    }


    // --- NOTE OPERATIONS ---
    fun selectNote(note: NoteEntity?) {
        _selectedNote.value = note
    }

    fun addNote(title: String, content: String, tags: String = "", reminderTime: Long? = null, attachments: String = "[]") {
        val currentPage = _selectedPage.value ?: return
        viewModelScope.launch {
            val note = repository.createNote(
                pageId = currentPage.id,
                title = title,
                content = content,
                tags = tags,
                attachments = attachments,
                reminderTime = reminderTime,
                userEmail = currentEmail.value
            )
            _selectedNote.value = note
        }
    }

    fun saveNote(note: NoteEntity) {
        viewModelScope.launch {
            var finalNote = note
            val email = currentEmail.value
            if (email.isNotEmpty() && finalNote.userEmail != email) {
                finalNote = finalNote.copy(userEmail = email)
            }
            if (syncManager.isConnected.value) {
                finalNote = syncManager.uploadAttachmentsForNote(finalNote)
            }
            repository.updateNote(finalNote)
            if (_selectedNote.value?.id == finalNote.id) {
                _selectedNote.value = finalNote
            }
            if (syncManager.isConnected.value) {
                triggerDriveSync()
            }
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.deleteNoteSoft(note.id)
            if (_selectedNote.value?.id == note.id) {
                _selectedNote.value = null
            }
            if (syncManager.isConnected.value) {
                triggerDriveSync()
            }
        }
    }

    fun restoreNote(id: String) {
        viewModelScope.launch {
            repository.restoreNote(id)
            if (syncManager.isConnected.value) {
                triggerDriveSync()
            }
        }
    }

    fun deleteNotePermanently(note: NoteEntity) {
        viewModelScope.launch {
            repository.deleteNotePermanent(note)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash(currentEmail.value)
        }
    }

    fun togglePinNote(note: NoteEntity) {
        viewModelScope.launch {
            val updated = note.copy(isPinned = !note.isPinned)
            repository.updateNote(updated)
            if (_selectedNote.value?.id == note.id) {
                _selectedNote.value = updated
            }
        }
    }


    // --- SEARCH ---
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }


    // --- DRIVE SYNC ---
    fun triggerDriveSync() {
        viewModelScope.launch {
            syncManager.synchronize()
            // After sync completes, if there is no selected book, automatically select the first available restored book and its first page!
            if (_selectedBook.value == null) {
                val email = currentEmail.value
                val availableBooks = repository.getAllBooksList(email)
                if (availableBooks.isNotEmpty()) {
                    val firstBook = availableBooks.first()
                    _selectedBook.value = firstBook
                    val bookPages = repository.getPagesForBookList(firstBook.id)
                    if (bookPages.isNotEmpty()) {
                        _selectedPage.value = bookPages.first()
                    }
                }
            }
        }
    }

    fun createDriveDatabaseStructure() {
        viewModelScope.launch {
            syncManager.createDriveDatabaseStructure()
        }
    }


    // --- AI INTEL AGENT ACTIONS ---

    fun clearAiMessage() {
        _aiMessage.value = null
    }

    fun generateAISuggestions() {
        viewModelScope.launch {
            _aiLoading.value = true
            val currentNotes = notes.value
            if (currentNotes.isEmpty()) {
                _aiSuggestions.value = listOf(
                    "Escribe tu primera nota para obtener sugerencias inteligentes.",
                    "Crea un plan diario en una nota para organizar tus ideas.",
                    "Agrega etiquetas para organizar tus notas."
                )
                _aiLoading.value = false
                return@launch
            }

            if (_selectedAiEngine.value == AiEngineType.GOOGLE_AI_STUDIO) {
                val activeModelId = _aiSettingsState.value.selectedModel
                val summary = currentNotes.take(5).joinToString("\n") { "• ${it.title}: ${it.content.take(80)}" }
                val suggestions = try {
                    val prompt = "En base a estas notas del usuario:\n$summary\nGenera 3 sugerencias breves de qué nota escribir o investigar a continuación. Devuelve exactamente 3 líneas separadas por salto de línea sin viñetas."
                    val resp = googleAiStudio.generateContent(
                        prompt = prompt,
                        includeThoughtBlock = false
                    )
                    resp.lines().map { it.trim().removePrefix("•").removePrefix("-").trim() }.filter { it.isNotBlank() }.take(3)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("AetherViewModel", "Error generando sugerencias con Google AI Studio ($activeModelId)", e)
                    _aiMessage.value = com.example.util.sanitizeSensitiveDetails(
                        e.localizedMessage.orEmpty()
                    ).ifBlank { "No se pudo conectar con Google AI Studio ($activeModelId)." }
                    emptyList()
                }
                _aiSuggestions.value = if (suggestions.isNotEmpty()) suggestions else listOf(
                    "Sintetiza tus notas recientes con Google AI Studio.",
                    "Crea una nueva nota de resumen para tus proyectos.",
                    "Organiza tus notas clave con etiquetas y colores."
                )
                _aiLoading.value = false
                return@launch
            }

            if (modelVerifier.checkCurrentStatus() is ModelDownloadStatus.Ready) {
                val summary = currentNotes.take(5).joinToString("\n") { "• ${it.title}: ${it.content.take(80)}" }
                val reqContext = localLlm.calculateElasticContext(summary.length + 200, 300)
                localLlm.ensureModelLoaded(modelVerifier.modelFile, reqContext)
                val suggestions = localLlm.generateSuggestions(summary)
                _aiSuggestions.value = suggestions
            } else {
                _aiSuggestions.value = listOf(
                    "Sintetiza tus notas actuales con Qwen local.",
                    "Crea un recordatorio para revisar tus notas al final del día.",
                    "Descarga el modelo Qwen en Ajustes para análisis 100% offline."
                )
            }
            _aiLoading.value = false
        }
    }

    fun applyAiModificationToParagraph(
        note: NoteEntity,
        paragraphIndex: Int,
        originalText: String,
        instruction: String,
        onSuccess: (String) -> Unit
    ) {
        viewModelScope.launch {
            _aiLoading.value = true
            _aiMessage.value = "Mejorando párrafo..."
            val prompt = """
                Párrafo original:
                $originalText

                Instrucción del usuario:
                $instruction
            """.trimIndent()
            val sysPrompt = """
                Eres un asistente experto de escritura. Tu tarea es reescribir o mejorar ÚNICAMENTE el párrafo o texto proporcionado siguiendo la instrucción.
                REGLAS CRÍTICAS:
                1. Devuelve ÚNICAMENTE el texto final generado, sin introducciones, sin saludos, sin explicaciones ni comillas envolventes.
                2. Si la instrucción pide corregir, devuelve el párrafo corregido. Si pide expandir, devuelve el párrafo expandido. Si pide resumir, devuelve la síntesis en un párrafo o lista concisa.
            """.trimIndent()

            if (_selectedAiEngine.value == AiEngineType.GOOGLE_AI_STUDIO) {
                val activeModelId = _aiSettingsState.value.selectedModel
                var errorHandled = false
                val response = try {
                    googleAiStudio.generateContent(
                        prompt = prompt,
                        systemInstruction = sysPrompt,
                        includeThoughtBlock = false
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("AetherViewModel", "Error mejorando párrafo con Google AI Studio ($activeModelId)", e)
                    _aiMessage.value = com.example.util.sanitizeSensitiveDetails(
                        e.localizedMessage.orEmpty()
                    ).ifBlank { "Error al mejorar el párrafo con Google AI Studio ($activeModelId)." }
                    errorHandled = true
                    null
                }
                val clean = response?.trim()?.removeSurrounding("\"")?.removeSurrounding("```")?.trim()
                if (!clean.isNullOrBlank()) {
                    onSuccess(clean)
                    _aiMessage.value = "Párrafo actualizado con $activeModelId"
                } else if (!errorHandled) {
                    _aiMessage.value = "No se pudieron aplicar cambios al párrafo."
                }
                _aiLoading.value = false
                return@launch
            }

            if (modelVerifier.checkCurrentStatus() !is ModelDownloadStatus.Ready) {
                _aiMessage.value = "Descarga primero el modelo local Aura en Ajustes de IA."
                _aiLoading.value = false
                return@launch
            }
            val reqContext = localLlm.calculateElasticContext(prompt.length + sysPrompt.length, 512)
            localLlm.ensureModelLoaded(modelVerifier.modelFile, reqContext)
            val response = localLlm.generateResponse(prompt, sysPrompt)
            val clean = response.trim().removeSurrounding("\"").removeSurrounding("```").trim()
            if (clean.isNotBlank()) {
                onSuccess(clean)
                _aiMessage.value = "Párrafo actualizado con Aura"
            } else {
                _aiMessage.value = "No se pudieron aplicar cambios al párrafo."
            }
            _aiLoading.value = false
        }
    }

    fun applyAiModificationToNote(note: NoteEntity, instruction: String) {
        viewModelScope.launch {
            _aiLoading.value = true
            _aiMessage.value = "Modificando nota..."

            if (_selectedAiEngine.value == AiEngineType.GOOGLE_AI_STUDIO) {
                val activeModelId = _aiSettingsState.value.selectedModel
                val readableContent = com.example.convertBlocksToMarkdown(note.content).ifBlank { note.content }
                val prompt = "Título actual: ${note.title}\nContenido actual:\n$readableContent\n\nInstrucción:\n$instruction\n\nDevuelve la nota actualizada en formato:\nTITULO: <nuevo o mismo titulo>\nCONTENIDO: <nuevo contenido completo en Markdown compatible (headings #, ##, desplegables <details><summary>...</summary>...</details>, listas, estilos **, *, <u>, [color:...])>"
                var errorHandled = false
                val response = try {
                    googleAiStudio.generateContent(
                        prompt = prompt,
                        systemInstruction = "Eres un asistente editorial experto en UTK Notes. Modifica la nota siguiendo rigurosamente las instrucciones del usuario.",
                        includeThoughtBlock = false
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("AetherViewModel", "Error modificando nota con Google AI Studio ($activeModelId)", e)
                    _aiMessage.value = com.example.util.sanitizeSensitiveDetails(
                        e.localizedMessage.orEmpty()
                    ).ifBlank { "Error al modificar la nota con Google AI Studio." }
                    errorHandled = true
                    null
                }
                if (!response.isNullOrBlank()) {
                    val cleanResp = com.example.util.ChatNotePipeline.extractThoughtAndBody(response).second
                    val (newTitle, rawBody) = if (cleanResp.contains("CONTENIDO:", ignoreCase = true)) {
                        val t = cleanResp.substringAfter("TITULO:", note.title).substringBefore("CONTENIDO:").trim()
                        val c = cleanResp.substringAfter("CONTENIDO:").trim()
                        Pair(t.ifBlank { note.title }, c)
                    } else {
                        Pair(note.title, cleanResp)
                    }
                    val parsedBlocks = com.example.parseTextContentToBlocks(rawBody)
                    val trimmedBlocks = com.example.stripLeadingDuplicateTitleBlock(parsedBlocks, newTitle)
                        .ifEmpty { parsedBlocks }
                        .ifEmpty { listOf(com.example.EditorBlock.Text(content = rawBody)) }
                    val updatedNote = note.copy(
                        title = newTitle,
                        content = com.example.serializeBlocks(trimmedBlocks),
                        updatedAt = System.currentTimeMillis(),
                        isSynced = false
                    )
                    repository.updateNote(updatedNote)
                    _selectedNote.value = updatedNote
                    _aiMessage.value = "Nota modificada exitosamente con $activeModelId"
                } else if (!errorHandled) {
                    _aiMessage.value = "No se pudieron aplicar cambios con Google AI Studio."
                }
                _aiLoading.value = false
                return@launch
            }

            if (modelVerifier.checkCurrentStatus() !is ModelDownloadStatus.Ready) {
                _aiMessage.value = "Descarga primero el modelo local Qwen en Ajustes de IA."
                _aiLoading.value = false
                return@launch
            }
            val reqContext = localLlm.calculateElasticContext(note.title.length + note.content.length + instruction.length + 500, 1024)
            localLlm.ensureModelLoaded(modelVerifier.modelFile, reqContext)
            val result = localLlm.modifyNote(
                title = note.title,
                content = note.content,
                instruction = instruction
            )
            if (result != null) {
                val updatedNote = note.copy(
                    title = result.first,
                    content = result.second,
                    updatedAt = System.currentTimeMillis(),
                    isSynced = false
                )
                repository.updateNote(updatedNote)
                _selectedNote.value = updatedNote
                _aiMessage.value = "Nota modificada exitosamente con Qwen local!"
            } else {
                _aiMessage.value = "No se pudieron aplicar cambios con el modelo local."
            }
            _aiLoading.value = false
        }
    }

    fun createNoteWithAI(instruction: String) {
        val currentPage = _selectedPage.value ?: return
        viewModelScope.launch {
            _aiLoading.value = true
            _aiMessage.value = "Generando nueva nota..."

            if (_selectedAiEngine.value == AiEngineType.GOOGLE_AI_STUDIO) {
                val activeModelId = _aiSettingsState.value.selectedModel
                val prompt = "Crea una nota completa, bien estructurada y detallada en español sobre: $instruction\nFormato de salida:\nTITULO: <titulo breve y representativo>\nCONTENIDO: <contenido completo estructurado en Markdown compatible con UTK Notes>"
                var errorHandled = false
                val response = try {
                    googleAiStudio.generateContent(
                        prompt = prompt,
                        systemInstruction = "Eres Aura impulsada por $activeModelId. Redacta notas informativas, claras y útiles con formato Markdown estructurado.",
                        includeThoughtBlock = false
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("AetherViewModel", "Error creando nota con Google AI Studio ($activeModelId)", e)
                    _aiMessage.value = com.example.util.sanitizeSensitiveDetails(
                        e.localizedMessage.orEmpty()
                    ).ifBlank { "Error al crear la nota con Google AI Studio ($activeModelId)." }
                    errorHandled = true
                    null
                }
                if (!response.isNullOrBlank()) {
                    val cleanResp = com.example.util.ChatNotePipeline.extractThoughtAndBody(response).second
                    val (newTitle, rawBody) = if (cleanResp.contains("CONTENIDO:", ignoreCase = true)) {
                        val t = cleanResp.substringAfter("TITULO:", "Nota de Aura").substringBefore("CONTENIDO:").trim()
                        val c = cleanResp.substringAfter("CONTENIDO:").trim()
                        Pair(t.ifBlank { com.example.util.ChatNotePipeline.extractTitleFromNoteMarkdown(c) }, c)
                    } else {
                        Pair(com.example.util.ChatNotePipeline.extractTitleFromNoteMarkdown(cleanResp), cleanResp)
                    }
                    val parsedBlocks = com.example.parseTextContentToBlocks(rawBody)
                    val trimmedBlocks = com.example.stripLeadingDuplicateTitleBlock(parsedBlocks, newTitle)
                        .ifEmpty { parsedBlocks }
                        .ifEmpty { listOf(com.example.EditorBlock.Text(content = rawBody)) }
                    val newNote = repository.createNote(
                        pageId = currentPage.id,
                        title = newTitle,
                        content = com.example.serializeBlocks(trimmedBlocks),
                        tags = "Gemini, IA",
                        userEmail = currentEmail.value
                    )
                    _selectedNote.value = newNote
                    _aiMessage.value = "Nota creada con $activeModelId"
                } else if (!errorHandled) {
                    _aiMessage.value = "No se pudo generar la nota con Google AI Studio."
                }
                _aiLoading.value = false
                return@launch
            }

            if (modelVerifier.checkCurrentStatus() !is ModelDownloadStatus.Ready) {
                _aiMessage.value = "Descarga primero el modelo local Qwen en Ajustes de IA."
                _aiLoading.value = false
                return@launch
            }
            val reqContext = localLlm.calculateElasticContext(instruction.length + 500, 1024)
            localLlm.ensureModelLoaded(modelVerifier.modelFile, reqContext)
            val result = localLlm.modifyNote(
                title = "Idea Generada",
                content = "",
                instruction = "Crea una nota completa sobre: $instruction"
            )
            if (result != null) {
                val newNote = repository.createNote(
                    pageId = currentPage.id,
                    title = result.first,
                    content = result.second,
                    tags = "Generado, Qwen",
                    userEmail = currentEmail.value
                )
                _selectedNote.value = newNote
                _aiMessage.value = "Nueva nota generada con Qwen local!"
            } else {
                _aiMessage.value = "Error al generar nota con Qwen."
            }
            _aiLoading.value = false
        }
    }



    private val moshi = com.squareup.moshi.Moshi.Builder()
        .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()

    /**
     * Formatea exhaustiva y eficientemente miles de bloques o párrafos de una nota
     * para el análisis profundo y veloz del modelo local Aura.
     */
    fun formatNoteBlocksForLlm(content: String, maxChars: Int = 35_000): String {
        val blocks = try {
            com.example.parseBlocks(content)
        } catch (e: Exception) {
            emptyList<com.example.EditorBlock>()
        }
        if (blocks.isEmpty()) {
            return content.ifBlank { "(Nota sin contenido aún)" }.take(maxChars)
        }

        val totalBlocks = blocks.size
        if (totalBlocks <= 220) {
            val fullMarkdown = com.example.formatBlocksListToMarkdown(blocks)
            if (fullMarkdown.isBlank()) return "(Nota sin contenido aún)"
            return if (fullMarkdown.length > maxChars) {
                fullMarkdown.take(maxChars) + "\n...[Nota extensa condensada]"
            } else {
                fullMarkdown
            }
        }

        val keyBlocks = mutableListOf<String>()
        val regularTexts = mutableListOf<String>()

        blocks.forEachIndexed { idx, b ->
            when (b) {
                is com.example.EditorBlock.Text -> {
                    when {
                        b.isCollapsedHeader -> keyBlocks.add("<details><summary>${b.content}</summary>")
                        b.isHeader -> keyBlocks.add("## ${b.content}")
                        b.content.isNotBlank() -> regularTexts.add("• [Párrafo ${idx + 1}]: ${b.content}")
                    }
                }
                is com.example.EditorBlock.Todo -> keyBlocks.add("${if (b.isChecked) "[x]" else "[ ]"} ${b.content}")
                is com.example.EditorBlock.Table -> keyBlocks.add("[Tabla]: " + b.data.take(5).joinToString(" | ") { it.joinToString(",") })
                is com.example.EditorBlock.Quote -> keyBlocks.add("> ${b.content}")
                is com.example.EditorBlock.Callout -> keyBlocks.add("${b.emoji} ${b.content}")
                is com.example.EditorBlock.Code -> keyBlocks.add("[Código]: ${b.code.take(200)}")
                else -> Unit
            }
        }

        val sb = StringBuilder()
        sb.append("[Estructura de la nota: $totalBlocks bloques/párrafos analizados]\n")
        if (keyBlocks.isNotEmpty()) {
            sb.append("=== SECCIONES, ESTRUCTURA Y ELEMENTOS CLAVE ===\n")
            sb.append(keyBlocks.take(60).joinToString("\n"))
            sb.append("\n\n")
        }

        val textBudget = maxChars - sb.length - 200
        val sampleStride = maxOf(1, regularTexts.size / 80)
        val sampledTexts = regularTexts.filterIndexed { i, _ -> i % sampleStride == 0 }

        sb.append("=== DESARROLLO INTEGRAL DE PÁRRAFOS ===\n")
        var currentLen = 0
        for (t in sampledTexts) {
            if (currentLen + t.length > textBudget) break
            sb.append(t).append("\n")
            currentLen += t.length + 1
        }
        sb.append("\n[Total: $totalBlocks bloques analizados por Aura]")
        return sb.toString()
    }

enum class QueryComplexity {
    SIMPLE,
    INTERMEDIATE,
    COMPLEX
}

    fun detectQueryComplexity(message: String): QueryComplexity {
        val clean = message.trim().lowercase()
        val isIdentityOrSimple = clean in listOf(
            "hola", "hola!", "hola aura", "hola aura!", "buenos días", "buenas tardes", "buenas noches",
            "buenas", "hey", "saludos", "hi", "hello", "qué tal", "que tal", "¿qué tal?", "¿que tal?",
            "¿cómo estás?", "¿como estas?", "como estas", "cómo estás", "gracias", "muchas gracias",
            "adiós", "chao", "hasta luego", "ok", "vale", "bien", "perfecto"
        ) ||
        clean.contains("tu nombre") || clean.contains("te llamas") || clean.contains("quién eres") ||
        clean.contains("quien eres") || clean.contains("qué eres") || clean.contains("que eres") ||
        clean.contains("para qué sirves") || clean.contains("para que sirves") ||
        clean.contains("qué haces") || clean.contains("que haces") ||
        clean.contains("qué puedes hacer") || clean.contains("que puedes hacer") ||
        clean.contains("cuál es tu nombre") || clean.contains("cual es tu nombre") ||
        clean.contains("cómo te llamas") || clean.contains("como te llamas") ||
        (clean.length <= 25 && (clean.startsWith("hola") || clean.startsWith("buenas") || clean.startsWith("hey") || clean.startsWith("saludos")))

        if (isIdentityOrSimple) {
            return QueryComplexity.SIMPLE
        }

        val isComplex = clean.contains("resum", ignoreCase = true) ||
            clean.contains("analiz", ignoreCase = true) ||
            clean.contains("explic", ignoreCase = true) ||
            clean.contains("historia", ignoreCase = true) ||
            clean.contains("por qué", ignoreCase = true) ||
            clean.contains("por que", ignoreCase = true) ||
            clean.contains("diferencia", ignoreCase = true) ||
            clean.contains("compara", ignoreCase = true) ||
            clean.contains("desarrolla", ignoreCase = true) ||
            com.example.util.ChatNotePipeline.detectCreateNoteIntent(clean) ||
            com.example.util.ChatNotePipeline.detectPreserveFormatIntent(clean) ||
            clean.length > 80

        return if (isComplex) QueryComplexity.COMPLEX else QueryComplexity.INTERMEDIATE
    }

    fun buildAgentSystemPrompt(userQuery: String = "", complexity: QueryComplexity = QueryComplexity.INTERMEDIATE): String {
        return com.example.util.ChatNotePipeline.buildSystemPrompt(
            userQuery = userQuery,
            isGoogleAiStudio = _selectedAiEngine.value == AiEngineType.GOOGLE_AI_STUDIO,
            isSimpleQuery = complexity == QueryComplexity.SIMPLE,
            isComplexQuery = complexity == QueryComplexity.COMPLEX
        )
    }

    suspend fun parseAndApplyChatbotUpdates(response: String, userPrompt: String = ""): String {
        var cleanResponse = com.example.util.ChatNotePipeline.extractThoughtAndBody(response).second
            .let { com.example.util.ChatNotePipeline.sanitizeTechnicalTags(it) }

        if (cleanResponse.contains("Definición de la asistente personal") ||
            cleanResponse.contains("Propósito y funcionalidad prioritaria") ||
            cleanResponse.startsWith("Eres Aura, una asistente personal") ||
            cleanResponse.startsWith("Integrando toda la información")
        ) {
            cleanResponse = ""
        }

        val actionsPerformed = mutableListOf<String>()
        var createdNoteMarker: Pair<String, String>? = null

        // 1. Check for CREATE_BOOK
        val startTagBook = "[CREATE_BOOK_START]"
        val endTagBook = "[CREATE_BOOK_END]"
        if (cleanResponse.contains(startTagBook) && cleanResponse.contains(endTagBook)) {
            try {
                val startIdx = cleanResponse.indexOf(startTagBook)
                val endIdx = cleanResponse.indexOf(endTagBook)
                val blockContent = cleanResponse.substring(startIdx + startTagBook.length, endIdx).trim()
                val titleRegex = Regex("^TITLE:\\s*(.*)", RegexOption.MULTILINE)
                val title = titleRegex.find(blockContent)?.groupValues?.get(1)?.trim()
                if (!title.isNullOrEmpty()) {
                    val book = repository.createBook(title, userEmail = currentEmail.value)
                    _selectedBook.value = book
                    val page = repository.createPage(book.id, "Notas", userEmail = currentEmail.value)
                    _selectedPage.value = page
                    _selectedNote.value = null
                    actionsPerformed.add("📚 Libro \"$title\" creado exitosamente.")
                }
            } catch (e: Exception) {
                android.util.Log.e("AetherViewModel", "Error al crear libro", e)
            }
            val startIdx = cleanResponse.indexOf(startTagBook)
            val endIdx = cleanResponse.indexOf(endTagBook)
            cleanResponse = cleanResponse.substring(0, startIdx).trim() +
                "\n\n📚 *Libro creado en tu biblioteca.*\n\n" +
                cleanResponse.substring(endIdx + endTagBook.length).trim()
        }

        // 2. Check for CREATE_PAGE
        val startTagPage = "[CREATE_PAGE_START]"
        val endTagPage = "[CREATE_PAGE_END]"
        if (cleanResponse.contains(startTagPage) && cleanResponse.contains(endTagPage)) {
            try {
                val startIdx = cleanResponse.indexOf(startTagPage)
                val endIdx = cleanResponse.indexOf(endTagPage)
                val blockContent = cleanResponse.substring(startIdx + startTagPage.length, endIdx).trim()
                val titleRegex = Regex("^TITLE:\\s*(.*)", RegexOption.MULTILINE)
                val title = titleRegex.find(blockContent)?.groupValues?.get(1)?.trim()
                if (!title.isNullOrEmpty()) {
                    var targetBook = _selectedBook.value
                    if (targetBook == null) {
                        val books = repository.getAllBooksList(currentEmail.value)
                        targetBook = books.firstOrNull() ?: repository.createBook("Mis Notas", userEmail = currentEmail.value)
                        _selectedBook.value = targetBook
                    }
                    val page = repository.createPage(targetBook.id, title, userEmail = currentEmail.value)
                    _selectedPage.value = page
                    _selectedNote.value = null
                    actionsPerformed.add("📄 Página \"$title\" creada exitosamente.")
                }
            } catch (e: Exception) {
                android.util.Log.e("AetherViewModel", "Error al crear página", e)
            }
            val startIdx = cleanResponse.indexOf(startTagPage)
            val endIdx = cleanResponse.indexOf(endTagPage)
            cleanResponse = cleanResponse.substring(0, startIdx).trim() +
                "\n\n📄 *Página creada en el libro actual.*\n\n" +
                cleanResponse.substring(endIdx + endTagPage.length).trim()
        }

        // 3. Check for APPEND_NOTE
        val startTagAppend = "[APPEND_NOTE_START]"
        val endTagAppend = "[APPEND_NOTE_END]"
        if (cleanResponse.contains(startTagAppend) && cleanResponse.contains(endTagAppend)) {
            var appendContent: String? = null
            try {
                val startIdx = cleanResponse.indexOf(startTagAppend)
                val endIdx = cleanResponse.indexOf(endTagAppend)
                val blockContent = cleanResponse.substring(startIdx + startTagAppend.length, endIdx).trim()
                val contentStartMarker = "CONTENT_START"
                val contentEndMarker = "CONTENT_END"
                if (blockContent.contains(contentStartMarker) && blockContent.contains(contentEndMarker)) {
                    val cStartIdx = blockContent.indexOf(contentStartMarker)
                    val cEndIdx = blockContent.indexOf(contentEndMarker)
                    appendContent = blockContent.substring(cStartIdx + contentStartMarker.length, cEndIdx).trim()
                } else if (!blockContent.contains(contentStartMarker)) {
                    appendContent = blockContent
                }
                if (!appendContent.isNullOrBlank()) {
                    val currentNote = _selectedNote.value
                    if (currentNote != null) {
                        val existingBlocks = com.example.parseBlocks(currentNote.content)
                        val newBlocks = com.example.parseTextContentToBlocks(appendContent)
                        val combined = existingBlocks + newBlocks
                        val serialized = com.example.serializeBlocks(combined)
                        val updated = currentNote.copy(
                            content = serialized,
                            updatedAt = System.currentTimeMillis(),
                            isSynced = false
                        )
                        repository.updateNote(updated)
                        _selectedNote.value = updated
                        if (syncManager.isConnected.value) {
                            triggerDriveSync()
                        }
                    }
                    val targetTitle = _selectedNote.value?.title ?: "actual"
                    actionsPerformed.add("💡 Ideas añadidas a la nota \"$targetTitle\".")
                }
            } catch (e: Exception) {
                android.util.Log.e("AetherViewModel", "Error en APPEND_NOTE", e)
            }
            val startIdx = cleanResponse.indexOf(startTagAppend)
            val endIdx = cleanResponse.indexOf(endTagAppend)
            val appendDisplay = if (!appendContent.isNullOrBlank()) "\n\n$appendContent\n\n✅ *Ideas agregadas a tu nota actual.*\n" else "\n"
            cleanResponse = cleanResponse.substring(0, startIdx).trim() + appendDisplay + cleanResponse.substring(endIdx + endTagAppend.length).trim()
        }

        // 4. Check for UPDATE_NOTE
        val startTagUpdate = "[UPDATE_NOTE_START]"
        val endTagUpdate = "[UPDATE_NOTE_END]"
        if (cleanResponse.contains(startTagUpdate) && cleanResponse.contains(endTagUpdate)) {
            var updateTitle: String? = null
            var updateContent: String? = null
            try {
                val startIndex = cleanResponse.indexOf(startTagUpdate)
                val endIndex = cleanResponse.indexOf(endTagUpdate)
                val blockContent = cleanResponse.substring(startIndex + startTagUpdate.length, endIndex).trim()

                val idRegex = Regex("^ID:\\s*(.*)", RegexOption.MULTILINE)
                val idMatch = idRegex.find(blockContent)
                val id = idMatch?.groupValues?.get(1)?.trim()?.ifBlank { null } ?: _selectedNote.value?.id

                val titleRegex = Regex("^TITLE:\\s*(.*)", RegexOption.MULTILINE)
                val titleMatch = titleRegex.find(blockContent)
                val title = titleMatch?.groupValues?.get(1)?.trim()?.ifBlank { null } ?: _selectedNote.value?.title ?: "Nota"
                updateTitle = title

                val contentStartMarker = "CONTENT_START"
                val contentEndMarker = "CONTENT_END"
                if (blockContent.contains(contentStartMarker) && blockContent.contains(contentEndMarker)) {
                    val cStartIdx = blockContent.indexOf(contentStartMarker)
                    val cEndIdx = blockContent.indexOf(contentEndMarker)
                    updateContent = blockContent.substring(cStartIdx + contentStartMarker.length, cEndIdx).trim()
                }

                if (!id.isNullOrEmpty() && updateContent != null) {
                    val existingNote = repository.getNote(id) ?: _selectedNote.value
                    if (existingNote != null) {
                        val parsedBlocks = com.example.parseTextContentToBlocks(updateContent)
                        val trimmedBlocks = com.example.stripLeadingDuplicateTitleBlock(parsedBlocks, title).ifEmpty { parsedBlocks }
                        val serializedContent = com.example.serializeBlocks(trimmedBlocks)
                        val updatedNote = existingNote.copy(
                            title = title,
                            content = serializedContent,
                            updatedAt = System.currentTimeMillis(),
                            isSynced = false
                        )
                        repository.updateNote(updatedNote)
                        if (_selectedNote.value?.id == id || _selectedNote.value == null) {
                            _selectedNote.value = updatedNote
                        }
                        if (syncManager.isConnected.value) {
                            triggerDriveSync()
                        }
                    }
                    actionsPerformed.add("📝 Nota \"$title\" actualizada exitosamente.")
                }
            } catch (e: Exception) {
                android.util.Log.e("AetherViewModel", "Failed to parse UPDATE_NOTE tag block", e)
            }

            val updateIdx = cleanResponse.indexOf(startTagUpdate)
            val endUpdateIdx = cleanResponse.indexOf(endTagUpdate)
            val updateDisplay = if (!updateContent.isNullOrBlank()) {
                "\n\n### 📝 ${updateTitle ?: "Nota Actualizada"}\n\n$updateContent\n\n✅ *Nota actualizada en tu espacio de trabajo.*\n"
            } else "\n"
            cleanResponse = cleanResponse.substring(0, updateIdx).trim() + updateDisplay + cleanResponse.substring(endUpdateIdx + endTagUpdate.length).trim()
        }

        // 5. Check for CREATE_NOTE tag block
        val startTagCreate = "[CREATE_NOTE_START]"
        val endTagCreate = "[CREATE_NOTE_END]"
        if (cleanResponse.contains(startTagCreate) && cleanResponse.contains(endTagCreate)) {
            var createdTitle: String? = null
            var createdContent: String? = null
            try {
                val startIndex = cleanResponse.indexOf(startTagCreate)
                val endIndex = cleanResponse.indexOf(endTagCreate)
                val blockContent = cleanResponse.substring(startIndex + startTagCreate.length, endIndex).trim()

                val titleRegex = Regex("^TITLE:\\s*(.*)", RegexOption.MULTILINE)
                val title = titleRegex.find(blockContent)?.groupValues?.get(1)?.trim() ?: "Nueva Nota"
                createdTitle = title

                val contentStartMarker = "CONTENT_START"
                val contentEndMarker = "CONTENT_END"
                if (blockContent.contains(contentStartMarker) && blockContent.contains(contentEndMarker)) {
                    val cStartIdx = blockContent.indexOf(contentStartMarker)
                    val cEndIdx = blockContent.indexOf(contentEndMarker)
                    createdContent = blockContent.substring(cStartIdx + contentStartMarker.length, cEndIdx).trim()
                } else if (!blockContent.contains(contentStartMarker)) {
                    createdContent = blockContent
                }

                val safeBody = createdContent
                if (!safeBody.isNullOrBlank()) {
                    val targetPage = ensureDefaultTargetPage()
                    val parsedBlocks = com.example.parseTextContentToBlocks(safeBody)
                    val trimmedBlocks = com.example.stripLeadingDuplicateTitleBlock(parsedBlocks, title)
                        .ifEmpty { parsedBlocks }
                        .ifEmpty { listOf(com.example.EditorBlock.Text(content = safeBody)) }
                    val noteId = UUID.randomUUID().toString()
                    val note = repository.createNote(
                        pageId = targetPage.id,
                        title = title,
                        content = com.example.serializeBlocks(trimmedBlocks),
                        tags = "Generado, Aura",
                        attachments = "[]",
                        reminderTime = null,
                        userEmail = currentEmail.value,
                        id = noteId
                    )
                    _selectedNote.value = note
                    createdNoteMarker = note.id to note.title
                    if (syncManager.isConnected.value) {
                        triggerDriveSync()
                    }
                    actionsPerformed.add("✨ Nota \"$title\" creada exitosamente.")
                }
            } catch (e: Exception) {
                android.util.Log.e("AetherViewModel", "Failed to parse CREATE_NOTE tag block", e)
            }

            val createIdx = cleanResponse.indexOf(startTagCreate)
            val endCreateIdx = cleanResponse.indexOf(endTagCreate)
            val noteDisplay = if (!createdContent.isNullOrBlank()) {
                createdContent
            } else ""
            cleanResponse = listOf(
                cleanResponse.substring(0, createIdx).trim(),
                noteDisplay,
                cleanResponse.substring(endCreateIdx + endTagCreate.length).trim()
            ).filter { it.isNotBlank() }.joinToString("\n\n")
        }

        // 6. Detección natural amplia de creación/paso a nota (ej. "pásalo a una nota...", "crea una nota...", "guarda en una nota...")
        val isNaturalNoteCreation = com.example.util.ChatNotePipeline.detectCreateNoteIntent(userPrompt)
        if (isNaturalNoteCreation && actionsPerformed.isEmpty() && cleanResponse.length > 20) {
            try {
                val targetPage = ensureDefaultTargetPage()
                val extractedTitle = com.example.util.ChatNotePipeline.extractTitleFromNoteMarkdown(
                    cleanResponse,
                    fallback = "Nota de Aura"
                )
                val parsedBlocks = com.example.parseTextContentToBlocks(cleanResponse)
                val trimmedBlocks = com.example.stripLeadingDuplicateTitleBlock(parsedBlocks, extractedTitle)
                    .ifEmpty { parsedBlocks }
                    .ifEmpty { listOf(com.example.EditorBlock.Text(content = cleanResponse)) }
                val noteId = UUID.randomUUID().toString()
                val note = repository.createNote(
                    pageId = targetPage.id,
                    title = extractedTitle,
                    content = com.example.serializeBlocks(trimmedBlocks),
                    tags = "Generado, Aura",
                    attachments = "[]",
                    reminderTime = null,
                    userEmail = currentEmail.value,
                    id = noteId
                )
                _selectedNote.value = note
                createdNoteMarker = note.id to note.title
                if (syncManager.isConnected.value) {
                    triggerDriveSync()
                }
                actionsPerformed.add("Nota \"$extractedTitle\" guardada.")
            } catch (e: Exception) {
                android.util.Log.e("AetherViewModel", "Failed to auto-create note from natural intent", e)
            }
        }

        val trimmedClean = cleanResponse.trim()
        return if (createdNoteMarker != null) {
            val marker = com.example.util.ChatNotePipeline.buildNoteCreatedMarker(
                createdNoteMarker.first,
                createdNoteMarker.second
            )
            "$trimmedClean\n\n$marker".trim()
        } else {
            trimmedClean
        }
    }

    suspend fun queryCreationLabAi(
        prompt: String,
        systemInstruction: String = "Eres Aura, motor lingüístico y fonotáctico rápido, preciso y sin alucinaciones para creación de lenguas y conlangs. Responde estrictamente lo solicitado."
    ): String? {
        val status = modelVerifier.checkCurrentStatus()
        if (status !is ModelDownloadStatus.Ready) {
            return null
        }
        val isLoaded = localLlm.ensureModelLoaded(modelVerifier.modelFile)
        if (!isLoaded) return null

        return try {
            localLlm.generateResponse(
                prompt = prompt,
                systemPrompt = systemInstruction,
                history = emptyList(),
                onStatusUpdate = {}
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun sendMessage(
        message: String,
        imageBase64: String? = null,
        mimeType: String? = null,
        history: List<Pair<String, String>> = emptyList(),
        rawUserPrompt: String = message
    ): String? {
        val noteId = _selectedNote.value?.id
        val isGeminiEngine = _selectedAiEngine.value == AiEngineType.GOOGLE_AI_STUDIO
        val cleanMsg = rawUserPrompt.trim().lowercase()
        val isBareSummaryWithoutTopic = (cleanMsg == "dame un resumen" ||
            cleanMsg == "resumen" ||
            cleanMsg == "haz un resumen" ||
            cleanMsg == "hazme un resumen" ||
            cleanMsg == "analiza" ||
            cleanMsg == "analiza este pdf" ||
            cleanMsg == "analizar" ||
            cleanMsg == "resume esto") &&
            !message.contains("=== DOCUMENTO") &&
            !message.contains("=== NOTA") &&
            !message.contains("<attached_file>") &&
            !message.contains("<full_note>")

        if (isBareSummaryWithoutTopic && noteId == null) {
            return "¿Sobre qué tema, película o libro te gustaría el resumen? También puedes abrir una nota o adjuntar un archivo para que lo analice con detalle."
        }

        val complexity = detectQueryComplexity(rawUserPrompt)
        val agentPrompt = buildAgentSystemPrompt(rawUserPrompt, complexity)
        val isGreeting = complexity == QueryComplexity.SIMPLE
        val refsActiveNote = com.example.util.ChatNotePipeline.detectActiveNoteReference(rawUserPrompt)

        val memoryContext = if (noteId != null && !isGreeting && refsActiveNote) {
            val bank = memoryVault.getMemoryBank(noteId)
            if (bank.summary.isNotBlank()) "\n\n=== RESUMEN DE LA NOTA ACTUAL ===\n${bank.summary.take(500)}" else ""
        } else ""

        val finalSystemPrompt = agentPrompt + memoryContext

        val expectedOutputTokens = when (complexity) {
            QueryComplexity.SIMPLE -> 200
            QueryComplexity.INTERMEDIATE -> 600
            QueryComplexity.COMPLEX -> 1200
        }

        val imageDisclaimer = if (!imageBase64.isNullOrBlank() && !message.contains("INFORMACIÓN DE LA IMAGEN") && !message.contains("OCR")) {
            "\n\n[Nota: Aura procesará el contexto y texto asociado a tu imagen]."
        } else ""

        val maxLen = if (isGeminiEngine) 45_000 else ((expectedOutputTokens) * 12).coerceAtLeast(3000)
        val safeMessage = if (message.length > maxLen) message.take(maxLen) + "\n...[Contenido condensado para análisis global]" else message

        val isIndependentSummaryOrTopic = com.example.util.ChatNotePipeline.isIndependentGeneralTopic(rawUserPrompt) &&
            !message.contains("=== DOCUMENTO") && !message.contains("=== NOTA")

        val historyLimit = if (isGeminiEngine) 8 else 4
        val sanitizedHistory = history.filter { (_, text) ->
            val lower = text.lowercase()
            val isCorrupt = lower.contains("newy") ||
                lower.contains("entañando") ||
                lower.contains("pasadores del plato") ||
                lower.contains("no te acercas a redactar")
            !isCorrupt
        }

        val effectiveHistory = if (complexity == QueryComplexity.SIMPLE || isIndependentSummaryOrTopic) {
            emptyList()
        } else {
            sanitizedHistory.takeLast(historyLimit)
        }

        var isTransportError = false
        val rawResponse = if (isGeminiEngine) {
            val activeModelId = _aiSettingsState.value.selectedModel
            _chatbotStatusText.value = "Conectando con Google AI Studio ($activeModelId)..."
            _liveThinkingText.value = null
            try {
                googleAiStudio.generateContent(
                    prompt = safeMessage + imageDisclaimer,
                    systemInstruction = finalSystemPrompt,
                    imageBase64 = imageBase64,
                    imageMimeType = mimeType,
                    history = effectiveHistory,
                    includeThoughtBlock = true,
                    onStatusUpdate = { status ->
                        _chatbotStatusText.value = status
                        _liveThinkingText.value = status
                    }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: MissingApiKeyException) {
                isTransportError = true
                e.message ?: "Configura tu clave API de Google AI Studio en Ajustes de IA (engranaje) o cambia al motor 'Aura Local'."
            } catch (e: Exception) {
                isTransportError = true
                android.util.Log.e("AetherViewModel", "Error en Google AI Studio ($activeModelId)", e)
                val safeError = com.example.util.sanitizeSensitiveDetails(
                    e.localizedMessage ?: "Error de conexión"
                ).ifBlank { "Error de conexión con Google AI Studio ($activeModelId)" }
                "$safeError\n\nSi el problema es de cuota, puedes cambiar de modelo o alternar a 'Aura Local' en Ajustes de IA."
            } finally {
                _chatbotStatusText.value = null
                _liveThinkingText.value = null
            }
        } else {
            val status = modelVerifier.checkCurrentStatus()
            if (status !is ModelDownloadStatus.Ready) {
                return "El modelo local Aura aún no está descargado.\n\nVe a Ajustes de IA y pulsa 'Descargar Modelo' para habilitar las funciones de inteligencia artificial 100% privada y local, o activa el motor Google AI Studio con tu clave API gratuita."
            }

            val requiredContext = localLlm.calculateElasticContext(
                promptChars = message.length + finalSystemPrompt.length,
                expectedOutputTokens = expectedOutputTokens
            )

            val isLoaded = localLlm.ensureModelLoaded(modelVerifier.modelFile, requiredContext)
            if (!isLoaded) {
                return "Cargando el cerebro de Aura en la memoria local... Por favor, intenta de nuevo en unos segundos."
            }

            try {
                localLlm.generateResponse(
                    prompt = safeMessage + imageDisclaimer,
                    systemPrompt = finalSystemPrompt,
                    history = effectiveHistory,
                    maxTokens = expectedOutputTokens,
                    onStatusUpdate = { s -> _chatbotStatusText.value = s },
                    onThinkingUpdate = { thought ->
                        _liveThinkingText.value = com.example.util.ChatNotePipeline.sanitizeThoughtText(thought)
                            .takeIf { it.isNotBlank() }
                    }
                )
            } finally {
                _liveThinkingText.value = null
            }
        }

        if (isTransportError) {
            return rawResponse
        }

        if (noteId != null && refsActiveNote) {
            viewModelScope.launch {
                memoryVault.saveChat(
                    com.example.data.local.memory.NoteChatRecord(
                        chatId = UUID.randomUUID().toString(),
                        noteId = noteId,
                        title = rawUserPrompt.take(30),
                        messages = listOf(
                            com.example.data.local.memory.NoteChatMessage(role = "user", content = rawUserPrompt),
                            com.example.data.local.memory.NoteChatMessage(role = "assistant", content = rawResponse)
                        )
                    )
                )
            }
        }

        val (extractedThought, _) = com.example.util.ChatNotePipeline.extractThoughtAndBody(rawResponse)
        val cleanBody = parseAndApplyChatbotUpdates(rawResponse, rawUserPrompt).trim()

        return if (cleanBody.isNotBlank()) {
            if (extractedThought.isNotBlank()) {
                "<think>$extractedThought</think>\n\n$cleanBody"
            } else {
                cleanBody
            }
        } else if (extractedThought.isNotBlank()) {
            "<think>$extractedThought</think>\n\nAura ha procesado la información de tu solicitud."
        } else {
            if (complexity == QueryComplexity.SIMPLE) "¡Hola! ¿En qué puedo ayudarte hoy?" else "Aura ha procesado tu solicitud."
        }
    }

    fun sanitizeThoughtContent(rawThought: String, complexity: QueryComplexity = QueryComplexity.INTERMEDIATE): String {
        return com.example.util.ChatNotePipeline.sanitizeThoughtText(rawThought)
    }

    private fun sanitizeTechnicalTags(text: String): String {
        return com.example.util.ChatNotePipeline.sanitizeTechnicalTags(text)
    }

    fun cleanRawJsonForDisplay(text: String): String {
        val withoutThink = com.example.util.ChatNotePipeline.extractThoughtAndBody(text).second
        val (withoutMarker, _) = com.example.util.ChatNotePipeline.stripNoteCreatedMarker(withoutThink)
        val trimmed = withoutMarker.trim()
        if (!trimmed.startsWith("{") && !trimmed.contains("\"action\"")) {
            return trimmed
        }
        val contentRegex = Regex("\"content\"\\s*:\\s*\"([^\"]*)\"")
        val content = contentRegex.find(trimmed)?.groupValues?.get(1)?.trim()
        if (!content.isNullOrBlank()) {
            return com.example.util.ChatNotePipeline.sanitizeTechnicalTags(content.replace("\\n", "\n").replace("\\\"", "\""))
        }

        val explanationRegex = Regex("\"explanation\"\\s*:\\s*\"([^\"]*)\"")
        val explanation = explanationRegex.find(trimmed)?.groupValues?.get(1)?.trim()
        if (!explanation.isNullOrBlank()) {
            return com.example.util.ChatNotePipeline.sanitizeTechnicalTags(explanation.replace("\\n", "\n").replace("\\\"", "\""))
        }

        return com.example.util.ChatNotePipeline.sanitizeTechnicalTags(
            trimmed.replace(Regex("\"(action|target_scope|explanation|content)\"\\s*:\\s*"), "")
                .replace("{", "")
                .replace("}", "")
                .replace("\"", "")
                .trim()
        )
    }

    fun parseAuraJsonResponse(raw: String): AuraActionResponse {
        val (extractedThought, bodyAfterThink) = com.example.util.ChatNotePipeline.extractThoughtAndBody(raw)
        val (workingRaw, createdNoteMarker) = com.example.util.ChatNotePipeline.stripNoteCreatedMarker(bodyAfterThink)

        val trimmed = workingRaw
        val unwrapped = if (trimmed.startsWith("```json") && trimmed.endsWith("```")) {
            trimmed.removePrefix("```json").removeSuffix("```").trim()
        } else if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            trimmed.removePrefix("```").removeSuffix("```").trim()
        } else {
            trimmed
        }

        val jsonCandidate = if (unwrapped.contains("{") && unwrapped.contains("}")) {
            val s = unwrapped.indexOf('{')
            val e = unwrapped.lastIndexOf('}')
            if (s in 0 until e) unwrapped.substring(s, e + 1) else unwrapped
        } else unwrapped

        if (jsonCandidate.startsWith("{")) {
            try {
                val obj = org.json.JSONObject(jsonCandidate)
                val action = obj.optString("action", "conversation")
                val targetScope = obj.optString("target_scope", "chat")
                var explanation = com.example.util.ChatNotePipeline.sanitizeTechnicalTags(obj.optString("explanation", "").trim())
                var content = com.example.util.ChatNotePipeline.sanitizeTechnicalTags(obj.optString("content", "").trim())

                if (content.isBlank() && explanation.isNotBlank()) {
                    content = explanation
                    explanation = ""
                } else if (content.isNotBlank() && (explanation.isBlank() || explanation.equals(content, ignoreCase = true))) {
                    explanation = ""
                }

                if (content.isNotBlank() || explanation.isNotBlank()) {
                    return AuraActionResponse(
                        action = createdNoteMarker?.let { "note_created" } ?: action.ifBlank { "conversation" },
                        targetScope = createdNoteMarker?.first ?: targetScope.ifBlank { "chat" },
                        explanation = createdNoteMarker?.second ?: explanation,
                        content = content,
                        thought = extractedThought
                    )
                }
            } catch (_: Exception) {
            }

            try {
                if (unwrapped.contains("\"action\"") || unwrapped.contains("\"content\"") || unwrapped.contains("\"explanation\"")) {
                    val actionMatch = Regex("\"action\"\\s*:\\s*\"([^\"]+)\"").find(unwrapped)?.groupValues?.get(1)
                    val targetScopeMatch = Regex("\"target_scope\"\\s*:\\s*\"([^\"]+)\"").find(unwrapped)?.groupValues?.get(1)
                    var explanationMatch = Regex("\"explanation\"\\s*:\\s*\"([^\"]*)\"").find(unwrapped)?.groupValues?.get(1)?.trim() ?: ""

                    val contentStart = unwrapped.indexOf("\"content\"")
                    var cleanContent = ""
                    if (contentStart != -1) {
                        val colonIdx = unwrapped.indexOf(':', contentStart)
                        if (colonIdx != -1) {
                            val firstQuote = unwrapped.indexOf('"', colonIdx)
                            if (firstQuote != -1) {
                                val contentSub = unwrapped.substring(firstQuote + 1)
                                cleanContent = if (contentSub.endsWith("\"}") || contentSub.endsWith("\"}\n")) {
                                    contentSub.removeSuffix("}").trim().removeSuffix("\"")
                                } else if (contentSub.endsWith("}")) {
                                    contentSub.removeSuffix("}").trim().removeSuffix("\"")
                                } else {
                                    contentSub.removeSuffix("\"")
                                }.replace("\\n", "\n").replace("\\\"", "\"").trim()
                            }
                        }
                    }

                    cleanContent = com.example.util.ChatNotePipeline.sanitizeTechnicalTags(cleanContent)
                    explanationMatch = com.example.util.ChatNotePipeline.sanitizeTechnicalTags(explanationMatch)

                    if (cleanContent.isBlank() && explanationMatch.isNotBlank()) {
                        cleanContent = explanationMatch
                        explanationMatch = ""
                    } else if (cleanContent.isNotBlank() && cleanContent.equals(explanationMatch, ignoreCase = true)) {
                        explanationMatch = ""
                    }

                    if (cleanContent.isNotBlank() || explanationMatch.isNotBlank()) {
                        return AuraActionResponse(
                            action = createdNoteMarker?.let { "note_created" } ?: (actionMatch ?: "conversation"),
                            targetScope = createdNoteMarker?.first ?: (targetScopeMatch ?: "chat"),
                            explanation = createdNoteMarker?.second ?: explanationMatch,
                            content = cleanContent,
                            thought = extractedThought
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }

        return AuraActionResponse(
            action = if (createdNoteMarker != null) "note_created" else "conversation",
            targetScope = createdNoteMarker?.first ?: "chat",
            explanation = createdNoteMarker?.second ?: "",
            content = cleanRawJsonForDisplay(trimmed),
            thought = extractedThought
        )
    }

    /**
     * Aplica directamente la acción devuelta por Aura sobre la nota activa o seleccionada.
     */
    fun applyAuraAction(
        actionResponse: AuraActionResponse,
        targetNote: NoteEntity? = null,
        onSuccess: ((String) -> Unit)? = null
    ) {
        val note = targetNote ?: _selectedNote.value ?: return
        when (actionResponse.action) {
            "replace_full_note" -> {
                replaceActiveNoteContent(actionResponse.content, targetNote = note) { title ->
                    onSuccess?.invoke("Nota \"$title\" actualizada por completo")
                }
            }
            "replace_selection" -> {
                replaceSelectionInNote(actionResponse.content, targetNote = note) { title ->
                    onSuccess?.invoke("Selección actualizada en \"$title\"")
                }
            }
            "insert_at_cursor", "conversation" -> {
                appendContentToActiveNote(actionResponse.content, targetNote = note) { title ->
                    onSuccess?.invoke("Contenido insertado en \"$title\"")
                }
            }
            else -> {
                appendContentToActiveNote(actionResponse.content, targetNote = note) { title ->
                    onSuccess?.invoke("Contenido agregado a \"$title\"")
                }
            }
        }
    }

    fun replaceSelectionInNote(
        newContentText: String,
        targetNote: NoteEntity? = null,
        selectedText: String? = null,
        onDone: ((String) -> Unit)? = null
    ) {
        val current = targetNote ?: _selectedNote.value ?: return
        viewModelScope.launch {
            val existingBlocks = com.example.parseBlocks(current.content)
            val newBlocks = com.example.parseTextContentToBlocks(newContentText)
            val updatedBlocks = if (!selectedText.isNullOrBlank()) {
                val idx = existingBlocks.indexOfFirst { b ->
                    when (b) {
                        is com.example.EditorBlock.Text -> b.content.contains(selectedText.trim()) || selectedText.trim().contains(b.content.trim())
                        else -> false
                    }
                }
                if (idx != -1) {
                    existingBlocks.toMutableList().apply {
                        removeAt(idx)
                        addAll(idx, newBlocks)
                    }
                } else {
                    existingBlocks + newBlocks
                }
            } else {
                existingBlocks + newBlocks
            }

            val updated = current.copy(
                content = com.example.serializeBlocks(updatedBlocks),
                updatedAt = System.currentTimeMillis(),
                isSynced = false
            )
            repository.updateNote(updated)
            _selectedNote.value = updated
            if (syncManager.isConnected.value) {
                triggerDriveSync()
            }
            onDone?.invoke(updated.title)
        }
    }

    fun appendContentToActiveNote(textToAppend: String, targetNote: NoteEntity? = null, onDone: ((String) -> Unit)? = null) {
        val current = targetNote ?: _selectedNote.value ?: return
        viewModelScope.launch {
            val existingBlocks = com.example.parseBlocks(current.content)
            val newBlocks = com.example.parseTextContentToBlocks(textToAppend)
            val combined = existingBlocks + newBlocks
            val updated = current.copy(
                content = com.example.serializeBlocks(combined),
                updatedAt = System.currentTimeMillis(),
                isSynced = false
            )
            repository.updateNote(updated)
            _selectedNote.value = updated
            if (syncManager.isConnected.value) {
                triggerDriveSync()
            }
            onDone?.invoke(updated.title)
        }
    }

    fun replaceActiveNoteContent(newContentText: String, targetNote: NoteEntity? = null, onDone: ((String) -> Unit)? = null) {
        val current = targetNote ?: _selectedNote.value ?: return
        viewModelScope.launch {
            val newBlocks = com.example.parseTextContentToBlocks(newContentText)
            val updated = current.copy(
                content = com.example.serializeBlocks(newBlocks),
                updatedAt = System.currentTimeMillis(),
                isSynced = false
            )
            repository.updateNote(updated)
            _selectedNote.value = updated
            if (syncManager.isConnected.value) {
                triggerDriveSync()
            }
            onDone?.invoke(updated.title)
        }
    }
}
