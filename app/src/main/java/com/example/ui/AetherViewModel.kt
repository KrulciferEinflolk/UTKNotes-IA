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
import com.example.data.local.llm.LocalLlmManager
import com.example.data.local.llm.ModelDownloadVerifier
import com.example.data.local.llm.ModelDownloadStatus
import com.example.data.local.memory.NoteMemoryVaultManager
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
    val content: String = ""
)

class AetherViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = NotesRepository(database.notesDao())
    val syncManager = DriveSyncManager(application, database)
    val localLlm = LocalLlmManager(application)
    val modelVerifier = ModelDownloadVerifier(application)
    val memoryVault = NoteMemoryVaultManager(application)

    init {
        viewModelScope.launch {
            if (modelVerifier.checkCurrentStatus() is ModelDownloadStatus.Ready) {
                localLlm.ensureModelLoaded(modelVerifier.modelFile)
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

    fun setChatSession(sessionId: String?, messages: List<Pair<String, Boolean>>) {
        chatbotJob?.cancel()
        chatbotJob = null
        localLlm.stopGeneration()
        _currentChatSessionId.value = sessionId
        _chatMessages.value = messages
        _isChatbotSending.value = false
        _chatbotStatusText.value = null
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
    }

    fun sendChatbotMessage(
        userDisplayMsg: String,
        promptWithContext: String,
        imgB64: String? = null,
        imgMime: String? = null,
        alreadyAddedToChat: Boolean = false
    ) {
        val updatedList = if (alreadyAddedToChat) _chatMessages.value else (_chatMessages.value + (userDisplayMsg to true))
        _chatMessages.value = updatedList
        _isChatbotSending.value = true
        if (_chatbotStatusText.value.isNullOrBlank()) {
            _chatbotStatusText.value = "🧠 Aura está pensando..."
        }

        if (_currentChatSessionId.value == null) {
            _currentChatSessionId.value = java.util.UUID.randomUUID().toString()
        }
        val sId = _currentChatSessionId.value!!

        val serializeChatMessagesFunc: (List<Pair<String, Boolean>>) -> String = { list ->
            val array = org.json.JSONArray()
            for (pair in list) {
                val obj = org.json.JSONObject()
                obj.put("text", pair.first)
                obj.put("isUser", pair.second)
                array.put(obj)
            }
            array.toString()
        }

        viewModelScope.launch {
            val existing = repository.getChatSession(sId)
            val chatTitle = existing?.title ?: userDisplayMsg.take(35).ifBlank { "Conversación" }
            saveChatSession(sId, chatTitle, serializeChatMessagesFunc(updatedList))
        }

        chatbotJob?.cancel()
        chatbotJob = viewModelScope.launch {
            try {
                // Pass recent history turns to local LLM so it maintains multi-turn conversation context (up to 6 turns with up to 700 chars each)
                val recentHistory = _chatMessages.value.dropLast(1).takeLast(6).map { (text, isUser) ->
                    (if (isUser) "user" else "assistant") to text.take(700)
                }
                val response = sendMessage(promptWithContext, imgB64, imgMime, recentHistory)
                val finalResult = response ?: "Error al obtener respuesta de Aura"
                
                // Save to database regardless of current session UI state
                val dbSession = repository.getChatSession(sId)
                val currentMsgs = if (dbSession != null) {
                    deserializeChatMessages(dbSession.messagesJson)
                } else {
                    updatedList
                }
                val finalMessages = currentMsgs + (finalResult to false)
                val sessionTitle = dbSession?.title ?: userDisplayMsg.take(35).ifBlank { "Conversación" }
                saveChatSession(sId, sessionTitle, serializeChatMessagesFunc(finalMessages))

                // Update UI only if we are still in the same session
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
                    val finalMessages = _chatMessages.value + ("Error en la respuesta: ${e.message}" to false)
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

    /**
     * Exporta instantáneamente cualquier mensaje del chatbot a una nota en la base de datos local
     * sin necesidad de esperar inferencias secundarias de la IA.
     */
    fun exportMessageToNote(
        msgContent: String,
        asBlocks: Boolean,
        customTitle: String? = null,
        onSuccess: (NoteEntity) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                var targetPage = _selectedPage.value
                if (targetPage == null) {
                    var targetBook = _selectedBook.value
                    if (targetBook == null) {
                        val allBooks = repository.getAllBooksList(currentEmail.value)
                        targetBook = allBooks.firstOrNull() ?: repository.createBook("Mis Notas", userEmail = currentEmail.value)
                        _selectedBook.value = targetBook
                    }
                    val pages = repository.getPagesForBookList(targetBook.id)
                    targetPage = pages.firstOrNull() ?: repository.createPage(targetBook.id, "General", userEmail = currentEmail.value)
                    _selectedPage.value = targetPage
                }

                val lines = msgContent.lines().map { it.trim() }.filter { it.isNotBlank() }
                val extractedTitle = lines.firstOrNull { it.startsWith("#") }?.removePrefix("#")?.trim()
                    ?: lines.firstOrNull()?.take(40)?.replace(Regex("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ ]"), "")?.trim()
                    ?: (if (asBlocks) "Nota Estructurada de IA" else "Resumen de Aura")

                val title = customTitle ?: extractedTitle.ifBlank { "Nota de Aura" }

                val parsedBlocks = com.example.parseTextContentToBlocks(msgContent)
                val serializedContent = com.example.serializeBlocks(parsedBlocks)

                val note = repository.createNote(
                    pageId = targetPage.id,
                    title = title,
                    content = serializedContent,
                    tags = if (asBlocks) "Bloques, Aura" else "Resumen, Aura",
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

    /**
     * Envía un mensaje del usuario asegurando ejecución en segundo plano (viewModelScope),
     * de modo que aunque el usuario cierre el Chatbot o cambie de pantalla,
     * el procesamiento de imágenes, OCR e inferencia no se interrumpan.
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
        allNotes: List<NoteEntity>
    ) {
        val displayMsg = when {
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
        _chatbotStatusText.value = if (fUri != null || fName != null) "📄 Leyendo \"${fName ?: "documento"}\"..." else "🧠 Aura está pensando..."

        viewModelScope.launch {
            // Extract document text if URI is provided and content is not already extracted
            var actualFileContent = fContent
            if (fUri != null && actualFileContent.isNullOrBlank()) {
                _chatbotStatusText.value = "📄 Leyendo \"${fName ?: "documento"}\"..."
                actualFileContent = com.example.util.LocalMediaAnalyzer.extractDocumentText(context, fUri, fName)
            }

            // Local On-Device Image Analysis & OCR via ML Kit if image is provided
            var imageOcrText = ""
            if (imgUri != null) {
                _chatbotStatusText.value = "📷 Analizando imagen con OCR..."
                imageOcrText = com.example.util.LocalMediaAnalyzer.analyzeImage(context, imgUri)
            }

            // Estructura XML de contexto para Aura
            val sb = StringBuilder()

            // 1. <full_note>
            val activeTargetNote = pNote ?: _selectedNote.value
            if (activeTargetNote != null) {
                val formattedNote = formatNoteBlocksForLlm(activeTargetNote.content, maxChars = 3500)
                sb.append("<full_note>\n")
                sb.append(formattedNote)
                sb.append("\n</full_note>\n\n")
            } else if (allNotes.isNotEmpty() && rawUserMsg.contains("nota", ignoreCase = true)) {
                val matched = allNotes.find { rawUserMsg.contains(it.title, ignoreCase = true) } ?: allNotes.first()
                val formattedNote = formatNoteBlocksForLlm(matched.content, maxChars = 3000)
                sb.append("<full_note>\n")
                sb.append(formattedNote)
                sb.append("\n</full_note>\n\n")
            }

            // 2. <selected_text>
            if (!pText.isNullOrBlank()) {
                sb.append("<selected_text>\n")
                sb.append(pText.take(2000))
                sb.append("\n</selected_text>\n\n")
            }

            // 3. <cursor_position>
            if (activeTargetNote != null) {
                sb.append("<cursor_position>\n")
                sb.append("Al final de la nota activa (\"${activeTargetNote.title}\")")
                sb.append("\n</cursor_position>\n\n")
            }

            // 4. <attached_file>
            if (!actualFileContent.isNullOrBlank()) {
                val safeFile = if (actualFileContent.length > 5000) actualFileContent.take(5000) + "\n...[Documento extenso condensado]" else actualFileContent
                sb.append("<attached_file>\n")
                sb.append(safeFile)
                sb.append("\n</attached_file>\n\n")
            }

            // Contexto de imagen OCR si aplica
            if (imageOcrText.isNotBlank()) {
                sb.append("<attached_image_ocr>\n")
                sb.append(imageOcrText)
                sb.append("\n</attached_image_ocr>\n\n")
            }

            // 5. Notas adicionales mencionadas con @
            allNotes.forEach { note ->
                if (rawUserMsg.contains("@${note.title}", ignoreCase = true) && note.id != activeTargetNote?.id) {
                    val mdContent = com.example.convertBlocksToMarkdown(note.content)
                    val safeMd = if (mdContent.length > 2000) mdContent.take(2000) + "..." else mdContent
                    sb.append("<referenced_note title=\"${note.title}\">\n$safeMd\n</referenced_note>\n\n")
                }
            }

            // 6. <user_request>
            val isAnalysisRequest = rawUserMsg.contains("analiz", ignoreCase = true) ||
                                    rawUserMsg.contains("análisis", ignoreCase = true) ||
                                    rawUserMsg.contains("resum", ignoreCase = true) ||
                                    rawUserMsg.contains("sinteti", ignoreCase = true) ||
                                    rawUserMsg.contains("explica", ignoreCase = true) ||
                                    rawUserMsg.contains("revisa", ignoreCase = true) ||
                                    rawUserMsg.contains("qué opina", ignoreCase = true) ||
                                    rawUserMsg.contains("que opina", ignoreCase = true) ||
                                    rawUserMsg.contains("de qué trata", ignoreCase = true) ||
                                    rawUserMsg.contains("de que trata", ignoreCase = true)

            val baseRequest = if (rawUserMsg.isNotBlank()) {
                rawUserMsg
            } else if (!actualFileContent.isNullOrBlank()) {
                "Lee el documento adjunto y resume sus ideas clave integrándolas en un formato claro."
            } else if (imgUri != null) {
                "Analiza la imagen adjunta, transcribe su texto y describe sus elementos visuales."
            } else {
                "¿En qué puedes ayudarme con mis notas?"
            }

            val userRequestText = if (isAnalysisRequest && !rawUserMsg.contains("reemplaza", ignoreCase = true) && !rawUserMsg.contains("modifica", ignoreCase = true)) {
                "$baseRequest\n\n[INSTRUCCIÓN CLAVE: El usuario solicita un análisis explicativo y detallado en el chat. Utiliza action: \"conversation\" y target_scope: \"chat\". Desarrolla un análisis profundo, completo y multidimensional en Markdown sin resumir con viñetas cortadas ni intentar reemplazar la nota]."
            } else {
                baseRequest
            }

            sb.append("<user_request>\n")
            sb.append(userRequestText)
            sb.append("\n</user_request>")

            val finalMsgForApi = sb.toString()

            sendChatbotMessage(
                userDisplayMsg = displayMsg,
                promptWithContext = finalMsgForApi,
                imgB64 = imgB64,
                imgMime = imgMime,
                alreadyAddedToChat = true
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

    fun saveChatSession(id: String, title: String, messagesJson: String) {
        viewModelScope.launch {
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

            if (modelVerifier.checkCurrentStatus() is ModelDownloadStatus.Ready) {
                localLlm.ensureModelLoaded(modelVerifier.modelFile)
                val summary = currentNotes.take(5).joinToString("\n") { "• ${it.title}: ${it.content.take(80)}" }
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
            _aiMessage.value = "Aura mejorando párrafo..."
            if (modelVerifier.checkCurrentStatus() !is ModelDownloadStatus.Ready) {
                _aiMessage.value = "Descarga primero el modelo local Aura en Ajustes de IA."
                _aiLoading.value = false
                return@launch
            }
            localLlm.ensureModelLoaded(modelVerifier.modelFile)
            val prompt = """
                Párrafo original:
                $originalText

                Instrucción del usuario:
                $instruction
            """.trimIndent()
            val sysPrompt = """
                Eres Aura, asistente experta de escritura. Tu tarea es reescribir o mejorar ÚNICAMENTE el párrafo o texto proporcionado siguiendo la instrucción.
                REGLAS CRÍTICAS:
                1. Devuelve ÚNICAMENTE el texto final generado, sin introducciones, sin saludos, sin explicaciones ni comillas envolventes.
                2. Si la instrucción pide corregir, devuelve el párrafo corregido. Si pide expandir, devuelve el párrafo expandido. Si pide resumir, devuelve la síntesis en un párrafo o lista concisa.
            """.trimIndent()
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
            _aiMessage.value = "Qwen modificando nota..."
            if (modelVerifier.checkCurrentStatus() !is ModelDownloadStatus.Ready) {
                _aiMessage.value = "Descarga primero el modelo local Qwen en Ajustes de IA."
                _aiLoading.value = false
                return@launch
            }
            localLlm.ensureModelLoaded(modelVerifier.modelFile)
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
            _aiMessage.value = "Qwen generando nueva nota..."
            if (modelVerifier.checkCurrentStatus() !is ModelDownloadStatus.Ready) {
                _aiMessage.value = "Descarga primero el modelo local Qwen en Ajustes de IA."
                _aiLoading.value = false
                return@launch
            }
            localLlm.ensureModelLoaded(modelVerifier.modelFile)
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
    fun formatNoteBlocksForLlm(content: String, maxChars: Int = 3000): String {
        val blocks = try {
            com.example.parseBlocks(content)
        } catch (e: Exception) {
            emptyList<com.example.EditorBlock>()
        }
        if (blocks.isEmpty()) {
            return content.ifBlank { "(Nota sin contenido aún)" }.take(maxChars)
        }

        val totalBlocks = blocks.size
        // Si la nota tiene un tamaño estándar, formatear todos los bloques
        if (totalBlocks <= 150) {
            val fullText = blocks.mapNotNull { b ->
                when (b) {
                    is com.example.EditorBlock.Text -> b.content.takeIf { it.isNotBlank() }
                    is com.example.EditorBlock.Todo -> "${if (b.isChecked) "[x]" else "[ ]"} ${b.content}"
                    is com.example.EditorBlock.Table -> b.data.joinToString(" | ") { it.joinToString(",") }
                    is com.example.EditorBlock.Quote -> "> ${b.content}"
                    is com.example.EditorBlock.Callout -> "${b.emoji} ${b.content}"
                    is com.example.EditorBlock.Code -> b.code
                    else -> null
                }
            }.joinToString("\n")
            return if (fullText.length > maxChars) fullText.take(maxChars) + "\n...[Nota extensa condensada]" else fullText
        }

        // Para notas masivas con cientos o miles de bloques:
        // Priorizar encabezados, tablas, listas de tareas, llamadas y muestreo equitativo de todos los párrafos de texto
        val keyBlocks = mutableListOf<String>()
        val regularTexts = mutableListOf<String>()

        blocks.forEachIndexed { idx, b ->
            when (b) {
                is com.example.EditorBlock.Text -> {
                    if (b.isHeader) {
                        keyBlocks.add("## ${b.content}")
                    } else if (b.content.isNotBlank()) {
                        regularTexts.add("• [Párrafo ${idx + 1}]: ${b.content}")
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

        // Muestrear regularTexts de forma uniforme cubriendo el 100% del documento desde el inicio hasta el final
        val textBudget = maxChars - sb.length - 200
        val sampleStride = maxOf(1, regularTexts.size / 60)
        val sampledTexts = regularTexts.filterIndexed { i, _ -> i % sampleStride == 0 }
        
        sb.append("=== DESARROLLO INTEGRAL DE PÁRRAFOS ===\n")
        var currentLen = 0
        for (t in sampledTexts) {
            if (currentLen + t.length > textBudget) break
            sb.append(t).append("\n")
            currentLen += t.length + 1
        }
        sb.append("\n[Total: $totalBlocks bloques analizados integralmente por Aura]")
        return sb.toString()
    }

    fun buildAgentSystemPrompt(userQuery: String = ""): String {
        return """
Eres Aura, un asistente de escritura con inteligencia artificial integrado en una aplicación de notas modular. Tu tarea es analizar, dar formato, reescribir, editar, continuar, resumir o integrar documentos dentro de la nota del usuario.

<ESTRUCTURA_DEL_CONTEXTO>
El usuario te enviará el estado de la nota en formato Markdown limpio, incluyendo títulos (##), listas (-), casillas de verificación ([x]), tablas, citas (>), formato (negrita, etiquetas de color, tamaño), desplegables (toggles), separadores (---), etc.
Recibirás los siguientes campos dentro del mensaje:
1. <full_note>: El contenido completo de la nota actual.
2. <selected_text>: (Opcional) El texto, párrafo o bloque específico en el que el usuario está enfocado o que ha seleccionado.
3. <cursor_position>: (Opcional) El lugar exacto donde el usuario dejó de escribir.
4. <attached_file>: (Opcional) El texto extraído de un archivo de texto o PDF adjunto que debe ser integrado.
5. <user_request>: La instrucción, petición o pregunta directa del usuario.
</ESTRUCTURA_DEL_CONTEXTO>

<REGLAS_DE_SALIDA>
DEBES responder SIEMPRE con un ÚNICO objeto JSON válido y NADA MÁS. No incluyas texto de introducción, ni delimitadores de código (NO uses ```json), ni comentarios finales.

El esquema JSON debe seguir estrictamente esta estructura:
{
  "action": "<NOMBRE_DE_LA_ACCION>",
  "target_scope": "selected_text" | "full_note" | "cursor_position" | "chat",
  "explanation": "<Resumen breve y claro sobre el cambio o sugerencia realizado para el usuario>",
  "content": "<El resultado en Markdown sugerido para ser insertado en la app o respuesta conversacional>"
}

ACCIONES PERMITIDAS:
- "conversation": Úsalo para responder preguntas generales, saludos, y PRINCIPALMENTE cuando el usuario pida ANALIZAR, REVISAR, EXPLICAR, RESUMIR O COMENTAR la nota en el chat. (target_scope: "chat", explanation: breve resumen del análisis, content: análisis exhaustivo, enriquecido y profundo en Markdown con títulos ##, viñetas, citas y conclusiones). NUNCA uses "replace_selection" cuando te pidan un análisis o resumen explicativo.
- "replace_selection": Reemplaza únicamente el <selected_text> con el Markdown mejorado, corregido o formateado. ÚSALO SÓLO SI EL USUARIO PIDE EXPLÍCITAMENTE corregir, editar o cambiar esa selección.
- "replace_full_note": Reemplaza toda la nota (útil SÓLO cuando el usuario pide explícitamente reescribir la nota completa en el editor o cambiar su contenido global).
- "insert_at_cursor": Inserta nuevo contenido en Markdown (sugerencias para continuar la nota, texto adicional o datos integrados de un PDF) justo donde está el cursor o al final de la nota.
- "read_file": Solicita al backend que lea/extraiga texto de un archivo o PDF adjunto si aún no se ha proporcionado.
</REGLAS_DE_SALIDA>

<REGLAS_DE_COMPORTAMIENTO>
1. ANÁLISIS EXHAUSTIVO Y RESÚMENES EN CHAT: Cuando el usuario pida analizar, estudiar, resumir o explicar una nota o tema ("analiza mi nota", "qué opinas", "analizar", "resumen"), responde SIEMPRE con action: "conversation" y target_scope: "chat". Desarrolla un análisis exhaustivo, profundo, multidimensional y completo en el campo "content" usando Markdown enriquecido (introducción, tesis principal, desglose detallado punto por punto, implicaciones y conclusiones). NUNCA te limites a viñetas breves incompletas ni uses "replace_selection".
2. CONTINUACIÓN DE ESCRITURA: Mantén el tono, idioma, estilo y contexto de <full_note>. Continúa de forma fluida a partir de <cursor_position> o del final de la nota.
3. GRAMÁTICA Y REESCRITURA: Corrige gramática, puntuación y fluidez. Utiliza elementos enriquecidos de Markdown (tablas, destacados, listas, desplegables) cuando mejore la legibilidad.
4. INTEGRACIÓN DE PDF/ARCHIVOS: Transforma el texto plano de archivos externos en secciones organizadas en Markdown que coincidan con el tema de la nota.
5. SIN ALUCINACIONES: No inventes hechos al resumir o integrar documentos. Si falta información crítica, indícalo claramente en el campo "explanation".
</REGLAS_DE_COMPORTAMIENTO>

<EJEMPLOS_FEW_SHOT>

Ejemplo 1: Analizar una nota en profundidad (Explicación en Chat)
Entrada del usuario:
<full_note>
# Historia de los Elfos
Los elfos son criaturas inteligentes con vitalidad superior. Llegaron a ser dominantes aunque se desconocen sus orígenes y a qué se debe su inteligencia. Se han enfrentado a plagas y han aprendido a usar artefactos refinados.
</full_note>
<user_request>Analiza esta nota en profundidad.</user_request>

Respuesta de Aura:
{
  "action": "conversation",
  "target_scope": "chat",
  "explanation": "Análisis temático, narrativo e implicaciones históricas de la nota sobre los elfos.",
  "content": "## Análisis Exhaustivo de la Nota: Civilización Élfica\n\n### 1. Premisa y Rasgos Fundamentales\nEl texto establece a los elfos como una civilización de alta resiliencia y ventaja biológica (inteligencia y vitalidad superior). Sin embargo, el relato plantea un misterio fundacional: el origen desconocido de sus facultades.\n\n### 2. Puntos Clave y Dinámica Histórica\n- **Adaptabilidad Tecnológica**: Han transitado del combate primario al uso de artefactos refinados.\n- **Aislamiento Estratégico**: Su retiro hacia grupos más silenciosos sugiere un declive poblacional o una doctrina de discreción frente a amenazas externas.\n- **Resiliencia ante Plagas**: La mención de enfermedades y plagas indica que, a pesar de su vitalidad, son vulnerables a crisis biológicas.\n\n### 3. Conclusiones y Sugerencias de Expansión\nLa nota posee un gran potencial para desarrollarse en un ensayo histórico o ficha de lore detallada. Se recomienda explorar el origen de su tecnología y la causa de su repliegue."
}

Ejemplo 2: Continuar una nota en la posición del cursor
Entrada del usuario:
<full_note>
# Estrategias de Marketing 2026
- Redes sociales: Enfocar en video corto.
- Emailing: Lanzar campaña mensual.
</full_note>
<cursor_position>Al final de la lista</cursor_position>
<user_request>Continúa la nota con 2 ideas más.</user_request>

Respuesta de Aura:
{
  "action": "insert_at_cursor",
  "target_scope": "cursor_position",
  "explanation": "Se añadieron dos estrategias adicionales de marketing manteniendo el estilo de la lista.",
  "content": "- SEO y Blog: Publicar 2 artículos semanales enfocados en intenciones de búsqueda.\n- Colaboraciones: Crear alianzas con creadores de nicho."
}

Ejemplo 3: Corregir gramática de un párrafo seleccionado
Entrada del usuario:
<full_note>
## Resumen
Ayer fuemos al parque y no encontremos a nadie.
</full_note>
<selected_text>Ayer fuemos al parque y no encontremos a nadie.</selected_text>
<user_request>Corrige la gramática de este párrafo.</user_request>

Respuesta de Aura:
{
  "action": "replace_selection",
  "target_scope": "selected_text",
  "explanation": "Se corrigieron los errores verbales 'fuemos' y 'encontremos'.",
  "content": "Ayer fuimos al parque y no encontramos a nadie."
}

Ejemplo 4: Resumir y reemplazar la nota completa
Entrada del usuario:
<full_note>
...[Reunión de 3 horas sobre nuevo producto]...
</full_note>
<user_request>Reescribe la nota completa con un resumen ejecutivo.</user_request>

Respuesta de Aura:
{
  "action": "replace_full_note",
  "target_scope": "full_note",
  "explanation": "Se estructuró un resumen ejecutivo con los puntos de acción clave.",
  "content": "## Resumen Ejecutivo de la Reunión\n- **Objetivo**: Lanzamiento versión 2.0.\n- **Fecha clave**: 15 de Noviembre.\n- **Responsables**: Equipo de producto y diseño."
}

Ejemplo 5: Saludo o conversación general
Entrada del usuario:
<user_request>Hola Aura, ¿cómo estás?</user_request>

Respuesta de Aura:
{
  "action": "conversation",
  "target_scope": "chat",
  "explanation": "Saludo inicial y puesta a disposición.",
  "content": "¡Hola! Estoy muy bien y lista para ayudarte con tus notas. ¿En qué estás trabajando hoy?"
}
</EJEMPLOS_FEW_SHOT>
        """.trimIndent()
    }

    fun parseAndApplyChatbotUpdates(response: String, userPrompt: String = ""): String {
        var cleanResponse = response
        val actionsPerformed = mutableListOf<String>()

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
                    viewModelScope.launch {
                        val book = repository.createBook(title, userEmail = currentEmail.value)
                        _selectedBook.value = book
                        val page = repository.createPage(book.id, "Notas", userEmail = currentEmail.value)
                        _selectedPage.value = page
                        _selectedNote.value = null
                    }
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
                    viewModelScope.launch {
                        var targetBook = _selectedBook.value
                        if (targetBook == null) {
                            val books = repository.getAllBooksList(currentEmail.value)
                            targetBook = books.firstOrNull() ?: repository.createBook("Mis Notas", userEmail = currentEmail.value)
                            _selectedBook.value = targetBook
                        }
                        val page = repository.createPage(targetBook.id, title, userEmail = currentEmail.value)
                        _selectedPage.value = page
                        _selectedNote.value = null
                    }
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
                    viewModelScope.launch {
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
                    viewModelScope.launch {
                        val existingNote = repository.getNote(id) ?: _selectedNote.value
                        if (existingNote != null) {
                            val parsedBlocks = com.example.parseTextContentToBlocks(updateContent)
                            val serializedContent = com.example.serializeBlocks(parsedBlocks)
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

        // 5. Check for CREATE_NOTE
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
                val titleMatch = titleRegex.find(blockContent)
                val title = titleMatch?.groupValues?.get(1)?.trim() ?: "Nueva Nota"
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

                if (!createdContent.isNullOrBlank()) {
                    viewModelScope.launch {
                        var targetPage = _selectedPage.value
                        if (targetPage == null) {
                            var targetBook = _selectedBook.value
                            if (targetBook == null) {
                                val allBooks = repository.getAllBooksList(currentEmail.value)
                                targetBook = allBooks.firstOrNull() ?: repository.createBook("Mis Notas", userEmail = currentEmail.value)
                                _selectedBook.value = targetBook
                            }
                            val pages = repository.getPagesForBookList(targetBook.id)
                            targetPage = pages.firstOrNull() ?: repository.createPage(targetBook.id, "General", userEmail = currentEmail.value)
                            _selectedPage.value = targetPage
                        }

                        val parsedBlocks = com.example.parseTextContentToBlocks(createdContent)
                        val serializedContent = com.example.serializeBlocks(parsedBlocks)
                        val note = repository.createNote(
                            pageId = targetPage.id,
                            title = title,
                            content = serializedContent,
                            tags = "Generado, Qwen",
                            attachments = "[]",
                            reminderTime = null,
                            userEmail = currentEmail.value
                        )
                        _selectedNote.value = note
                        if (syncManager.isConnected.value) {
                            triggerDriveSync()
                        }
                    }
                    actionsPerformed.add("✨ Nota \"$title\" creada exitosamente.")
                }
            } catch (e: Exception) {
                android.util.Log.e("AetherViewModel", "Failed to parse CREATE_NOTE tag block", e)
            }

            val createIdx = cleanResponse.indexOf(startTagCreate)
            val endCreateIdx = cleanResponse.indexOf(endTagCreate)
            val noteDisplay = if (!createdContent.isNullOrBlank()) {
                "\n\n### 📝 ${createdTitle ?: "Nueva Nota"}\n\n$createdContent\n\n✅ *Nota guardada automáticamente en tu espacio de trabajo.*\n"
            } else "\n"
            cleanResponse = cleanResponse.substring(0, createIdx).trim() +
                            noteDisplay +
                            cleanResponse.substring(endCreateIdx + endTagCreate.length).trim()
        }

        // 6. Si el usuario pidió explícitamente crear una nota o resumen y el modelo no generó las etiquetas cuadradas
        val promptLower = userPrompt.lowercase()
        val isNoteCreationRequest = promptLower.contains("crea una nota") ||
                                    promptLower.contains("crear nota") ||
                                    promptLower.contains("haz una nota") ||
                                    promptLower.contains("guarda en una nota") ||
                                    promptLower.contains("guardar en una nota") ||
                                    promptLower.contains("crea un resumen") ||
                                    promptLower.contains("nueva nota")

        if (isNoteCreationRequest && actionsPerformed.isEmpty() && cleanResponse.length > 30) {
            val lines = cleanResponse.lines().map { it.trim() }.filter { it.isNotBlank() }
            val extractedTitle = lines.firstOrNull { it.startsWith("#") }?.removePrefix("#")?.trim()
                ?: lines.firstOrNull()?.take(40)?.replace(Regex("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ ]"), "")?.trim()
                ?: "Resumen de Aura"

            viewModelScope.launch {
                var targetPage = _selectedPage.value
                if (targetPage == null) {
                    var targetBook = _selectedBook.value
                    if (targetBook == null) {
                        val allBooks = repository.getAllBooksList(currentEmail.value)
                        targetBook = allBooks.firstOrNull() ?: repository.createBook("Mis Notas", userEmail = currentEmail.value)
                        _selectedBook.value = targetBook
                    }
                    val pages = repository.getPagesForBookList(targetBook.id)
                    targetPage = pages.firstOrNull() ?: repository.createPage(targetBook.id, "General", userEmail = currentEmail.value)
                    _selectedPage.value = targetPage
                }

                val parsedBlocks = com.example.parseTextContentToBlocks(cleanResponse)
                val serializedContent = com.example.serializeBlocks(parsedBlocks)
                val note = repository.createNote(
                    pageId = targetPage.id,
                    title = extractedTitle,
                    content = serializedContent,
                    tags = "Generado, Qwen",
                    attachments = "[]",
                    reminderTime = null,
                    userEmail = currentEmail.value
                )
                _selectedNote.value = note
                if (syncManager.isConnected.value) {
                    triggerDriveSync()
                }
            }
            cleanResponse = "$cleanResponse\n\n✅ *Nota \"$extractedTitle\" guardada automáticamente en tu espacio de trabajo.*"
        }

        val trimmedClean = cleanResponse.trim()
        return trimmedClean
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
        history: List<Pair<String, String>> = emptyList()
    ): String? {
        val noteId = _selectedNote.value?.id
        val agentPrompt = buildAgentSystemPrompt(message)
        val memoryContext = if (noteId != null) {
            val bank = memoryVault.getMemoryBank(noteId)
            if (bank.summary.isNotBlank()) "\n\n=== RESUMEN DE LA NOTA ACTUAL ===\n${bank.summary.take(400)}" else ""
        } else ""

        val finalSystemPrompt = agentPrompt + memoryContext
        
        // --- 100% LOCAL IA (Qwen) ---
        val status = modelVerifier.checkCurrentStatus()
        if (status !is ModelDownloadStatus.Ready) {
            return "El modelo local Aura (Qwen2.5) aún no está descargado.\n\nVe a Ajustes de IA y pulsa 'Descargar Modelo' para habilitar las funciones de inteligencia artificial 100% privada y local."
        }

        val isLoaded = localLlm.ensureModelLoaded(modelVerifier.modelFile)
        if (!isLoaded) {
            return "Cargando el cerebro de Aura en la memoria local... Por favor, intenta de nuevo en unos segundos."
        }

        // Only add generic image note if no local OCR/analysis has already been integrated
        val imageDisclaimer = if (!imageBase64.isNullOrBlank() && !message.contains("INFORMACIÓN DE LA IMAGEN") && !message.contains("OCR")) {
            "\n\n[Nota: Aura procesará el contexto y texto asociado a tu imagen]."
        } else ""

        val hasContinuation = message.contains("INSTRUCCIÓN CRÍTICA DE CONTINUACIÓN") ||
                              message.contains("continú", ignoreCase = true) ||
                              message.contains("continu", ignoreCase = true) ||
                              message.contains("complet", ignoreCase = true) ||
                              message.contains("dónde me qued", ignoreCase = true) ||
                              message.contains("donde me qued", ignoreCase = true) ||
                              message.contains("mantén el formato", ignoreCase = true) ||
                              message.contains("manten el formato", ignoreCase = true) ||
                              message.contains("en mi nota", ignoreCase = true) ||
                              message.contains("de mi nota", ignoreCase = true) ||
                              message.contains("para mi nota", ignoreCase = true) ||
                              message.contains("a mi nota", ignoreCase = true) ||
                              message.contains("la nota", ignoreCase = true)

        val effectiveSystemPrompt = finalSystemPrompt

        // Límite seguro optimizado para la ventana de contexto de 8192 tokens
        val maxLen = 8000
        val safeMessage = if (message.length > maxLen) message.take(maxLen) + "\n...[Contenido condensado para análisis global]" else message

        val rawResponse = localLlm.generateResponse(
            prompt = safeMessage + imageDisclaimer,
            systemPrompt = effectiveSystemPrompt,
            history = history,
            onStatusUpdate = { status ->
                _chatbotStatusText.value = status
            }
        )

        if (noteId != null) {
            viewModelScope.launch {
                memoryVault.saveChat(
                    com.example.data.local.memory.NoteChatRecord(
                        chatId = java.util.UUID.randomUUID().toString(),
                        noteId = noteId,
                        title = message.take(30),
                        messages = listOf(
                            com.example.data.local.memory.NoteChatMessage(role = "user", content = message),
                            com.example.data.local.memory.NoteChatMessage(role = "assistant", content = rawResponse)
                        )
                    )
                )
            }
        }

        val clean = parseAndApplyChatbotUpdates(rawResponse, message).trim()
        return clean.ifBlank { rawResponse.trim().ifBlank { "Aura ha procesado tu solicitud localmente." } }
    }

    /**
     * Parsea de forma resiliente la respuesta estructurada en JSON de Aura,
     * extrayendo action, target_scope, explanation y content.
     */
    fun parseAuraJsonResponse(raw: String): AuraActionResponse {
        val trimmed = raw.trim()
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

        // Intento 1: org.json.JSONObject estándar
        try {
            val obj = org.json.JSONObject(jsonCandidate)
            val action = obj.optString("action", "conversation")
            val targetScope = obj.optString("target_scope", "chat")
            val explanation = obj.optString("explanation", "")
            val content = obj.optString("content", "")
            if (content.isNotBlank() || explanation.isNotBlank()) {
                return AuraActionResponse(
                    action = action.ifBlank { "conversation" },
                    targetScope = targetScope.ifBlank { "chat" },
                    explanation = explanation,
                    content = content.ifBlank { jsonCandidate }
                )
            }
        } catch (ignored: Exception) {
            // Continuar con extracción resiliente
        }

        // Intento 2: Extracción resiliente por Regex para tolerar saltos de línea sin escapar
        try {
            if (unwrapped.contains("\"action\"") || unwrapped.contains("\"content\"")) {
                val actionMatch = Regex("\"action\"\\s*:\\s*\"([^\"]+)\"").find(unwrapped)?.groupValues?.get(1)
                val targetScopeMatch = Regex("\"target_scope\"\\s*:\\s*\"([^\"]+)\"").find(unwrapped)?.groupValues?.get(1)
                val explanationMatch = Regex("\"explanation\"\\s*:\\s*\"([^\"]+)\"").find(unwrapped)?.groupValues?.get(1)

                // Extraer el cuerpo de "content": "..."
                val contentStart = unwrapped.indexOf("\"content\"")
                if (contentStart != -1) {
                    val colonIdx = unwrapped.indexOf(':', contentStart)
                    if (colonIdx != -1) {
                        val firstQuote = unwrapped.indexOf('"', colonIdx)
                        if (firstQuote != -1) {
                            val contentSub = unwrapped.substring(firstQuote + 1)
                            val cleanContent = if (contentSub.endsWith("\"}") || contentSub.endsWith("\"}\n")) {
                                contentSub.removeSuffix("}").trim().removeSuffix("\"")
                            } else if (contentSub.endsWith("}")) {
                                contentSub.removeSuffix("}").trim().removeSuffix("\"")
                            } else {
                                contentSub.removeSuffix("\"")
                            }.replace("\\n", "\n").replace("\\\"", "\"")

                            return AuraActionResponse(
                                action = actionMatch ?: "conversation",
                                targetScope = targetScopeMatch ?: "chat",
                                explanation = explanationMatch ?: "",
                                content = cleanContent
                            )
                        }
                    }
                }
            }
        } catch (ignored: Exception) {
            // Fallback a texto crudo
        }

        // Intento 3: Fallback seguro a texto conversacional
        return AuraActionResponse(
            action = "conversation",
            targetScope = "chat",
            explanation = "",
            content = trimmed
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
