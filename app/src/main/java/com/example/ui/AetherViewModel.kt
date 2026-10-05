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
                // Pass recent history turns to local LLM so it maintains multi-turn conversation context
                val recentHistory = _chatMessages.value.dropLast(1).takeLast(6).map { (text, isUser) ->
                    (if (isUser) "user" else "assistant") to text
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

            val isSummaryIntent = rawUserMsg.contains("resum", ignoreCase = true) ||
                                  rawUserMsg.contains("sintetiz", ignoreCase = true) ||
                                  rawUserMsg.isBlank()

            var finalMsgForApi = if (rawUserMsg.isNotBlank()) {
                if (isSummaryIntent && !actualFileContent.isNullOrBlank()) {
                    "$rawUserMsg\n\n[Instrucción de resumen: Lee todo el documento y genera una síntesis global y unificada de todo el texto. NO desgloses página por página para evitar respuestas cortadas o saturadas. Presenta un resumen completo con título, resumen general, puntos clave con viñetas y conclusiones]."
                } else {
                    rawUserMsg
                }
            } else if (!actualFileContent.isNullOrBlank()) {
                "Por favor lee todo el documento adjunto \"${fName ?: "documento"}\", analízalo en su conjunto y genera un resumen global, conciso y bien estructurado (sin desglosar página por página), destacando el tema principal, ideas clave con viñetas y conclusiones."
            } else if (imgUri != null) {
                "Por favor analiza la imagen adjunta, explícame qué contiene y describe el texto y elementos visuales presentes."
            } else {
                "¿En qué puedes ayudarme con mis notas?"
            }

            // Local On-Device Image Analysis & OCR via ML Kit
            if (imgUri != null) {
                _chatbotStatusText.value = "📷 Analizando imagen con OCR..."
                val analysis = com.example.util.LocalMediaAnalyzer.analyzeImage(context, imgUri)
                finalMsgForApi += "\n\n$analysis"
            }

            // Append screen note context
            if (pNote != null) {
                val mdContent = com.example.convertBlocksToMarkdown(pNote.content)
                finalMsgForApi += "\n\n[Contexto - Nota de pantalla (ID: \"${pNote.id}\"): \"${pNote.title}\"\nContenido en Markdown:\n$mdContent]"
            } else if (_selectedNote.value != null) {
                val current = _selectedNote.value!!
                val mdContent = com.example.convertBlocksToMarkdown(current.content)
                finalMsgForApi += "\n\n[Contexto - Nota de pantalla actual (ID: \"${current.id}\"): \"${current.title}\"\nContenido en Markdown:\n$mdContent]"
            }

            // Append paragraph citation context
            if (pText != null) {
                finalMsgForApi += "\n\n[Contexto - Párrafo citado de la nota:\n\"$pText\"]"
            }

            // Append attached file context
            if (!actualFileContent.isNullOrBlank()) {
                finalMsgForApi += "\n\n[Contexto - Archivo Adjunto \"${fName ?: "archivo"}\":\n$actualFileContent]"
            }

            // Append inline @ mentioned notes context!
            allNotes.forEach { note ->
                if (rawUserMsg.contains("@${note.title}", ignoreCase = true)) {
                    val mdContent = com.example.convertBlocksToMarkdown(note.content)
                    finalMsgForApi += "\n\n[Contexto - Nota Mencionada (ID: \"${note.id}\") \"${note.title}\":\n$mdContent]"
                }
            }

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

    fun buildAgentSystemPrompt(): String {
        val currentBook = _selectedBook.value
        val currentPage = _selectedPage.value
        val currentNote = _selectedNote.value

        val sb = StringBuilder()
        sb.append(com.example.data.local.memory.NoteMemoryVaultManager.PERMANENT_CORE_MEMORY)
        sb.append("\nEres Aura (nombre completo: Aura Kioko), el Agente Autónomo Inteligente de UTK Notes. Tienes PODER Y PERMISOS TOTALES para crear, editar, organizar y administrar libros, páginas y notas del usuario. Tu identidad inmutable es Aura Kioko.\n\n")
        sb.append("ESTADO ACTUAL:\n")
        sb.append("• Libro seleccionado: ${currentBook?.title ?: "Ninguno"}\n")
        sb.append("• Página seleccionada: ${currentPage?.title ?: "Ninguna"}\n")
        sb.append("• Nota seleccionada: ${currentNote?.let { "\"${it.title}\" (ID: ${it.id})" } ?: "Ninguna"}\n\n")

        sb.append("DIRECTRICES DE RESPUESTA:\n")
        sb.append("1. CONVERSACIÓN GENERAL, PREGUNTAS Y ANÁLISIS DE ARCHIVOS/IMÁGENES:\n")
        sb.append("   - Si el usuario te hace una pregunta, consulta tus conocimientos, o te pide analizar un archivo de texto, documento o imagen adjunta, responde de forma directa, inteligente, clara, agradable y bien estructurada en español.\n")
        sb.append("   - Utiliza formato Markdown enriquecido para estructurar tus respuestas: títulos principales con '# ', subtítulos con '## ' o '### ', negritas con '**palabra clave**', listas con viñetas ('- ' o '• ') y listas ordenadas con '1. ', '2. '.\n")
        sb.append("   - REGLA FUNDAMENTAL PARA RESÚMENES DE DOCUMENTOS Y PDFs:\n")
        sb.append("     * Analiza el documento en su TOTALIDAD como una unidad global y coherente.\n")
        sb.append("     * ESTÁ ESTRICTAMENTE PROHIBIDO desglosar o resumir página por página (ej. 'Página 1: ...', 'Página 2: ...'). Hacerlo satura la memoria y corta la respuesta a la mitad.\n")
        sb.append("     * Genera un resumen global y unificado que incluya:\n")
        sb.append("       1. **Tema y Propósito General**: Breve descripción del objetivo central del documento.\n")
        sb.append("       2. **Resumen Ejecutivo**: 1 o 2 párrafos concisos explicando la idea y narrativa principal.\n")
        sb.append("       3. **Puntos Clave**: Lista de 4 a 6 viñetas con los datos, hallazgos o conceptos más importantes (usando negritas en los términos clave).\n")
        sb.append("       4. **Conclusión o Síntesis Final**: Conclusión clara del documento.\n")
        sb.append("     * Mantén una extensión moderada y completa (entre 250 y 450 palabras) para que el resumen NUNCA se corte a la mitad.\n")
        sb.append("   - NO agregues bloques de comando [CREATE_NOTE_START], [CREATE_BOOK_START], etc. si el usuario sólo te pide analizar, explicar o conversar.\n\n")
        sb.append("2. COMANDOS DE ACCIÓN EN EL ESPACIO DE TRABAJO (ÚNICAMENTE si el usuario te pide crear, guardar o modificar notas o libros):\n")
        sb.append("   - Incluye los siguientes bloques sólo cuando sea requerido gestionar el espacio de trabajo:\n\n")

        sb.append("1. CREAR LIBRO:\n")
        sb.append("[CREATE_BOOK_START]\n")
        sb.append("TITLE: Título del libro\n")
        sb.append("[CREATE_BOOK_END]\n\n")

        sb.append("2. CREAR PÁGINA (en el libro actual):\n")
        sb.append("[CREATE_PAGE_START]\n")
        sb.append("TITLE: Título de la página\n")
        sb.append("[CREATE_PAGE_END]\n\n")

        sb.append("3. CREAR NOTA:\n")
        sb.append("[CREATE_NOTE_START]\n")
        sb.append("TITLE: Título de la nota\n")
        sb.append("CONTENT_START\n")
        sb.append("Contenido de la nota con formato enriquecido...\n")
        sb.append("CONTENT_END\n")
        sb.append("[CREATE_NOTE_END]\n\n")

        sb.append("4. AÑADIR IDEAS A LA NOTA ACTUAL (sin borrar lo existente):\n")
        sb.append("[APPEND_NOTE_START]\n")
        sb.append("CONTENT_START\n")
        sb.append("Nuevas ideas o fragmentos para agregar...\n")
        sb.append("CONTENT_END\n")
        sb.append("[APPEND_NOTE_END]\n\n")

        sb.append("5. MODIFICAR/REESCRIBIR LA NOTA ACTUAL:\n")
        sb.append("[UPDATE_NOTE_START]\n")
        sb.append("TITLE: Título de la nota\n")
        sb.append("CONTENT_START\n")
        sb.append("Contenido completo modificado...\n")
        sb.append("CONTENT_END\n")
        sb.append("[UPDATE_NOTE_END]\n\n")

        sb.append("CAPACIDADES DE FORMATO Y ELEMENTOS GRÁFICOS PARA NOTAS:\n")
        sb.append("Al redactar contenido entre CONTENT_START y CONTENT_END, debes utilizar variedad de formatos según el contexto para que las notas sean visualmente atractivas, estructuradas y profesionales:\n")
        sb.append("• TAMAÑOS DE FUENTE Y JERARQUÍA:\n")
        sb.append("  - '# Título' para Título Principal (tamaño grande 24sp, negrita)\n")
        sb.append("  - '## Subtítulo' para Subtítulos o secciones principales (tamaño 20sp, negrita)\n")
        sb.append("  - '### Encabezado' para Encabezados de subsección (tamaño 18sp, negrita)\n")
        sb.append("  - '#### Sección menor' para divisiones temáticas (tamaño 16sp, negrita)\n")
        sb.append("  - Párrafos de texto normal (tamaño estándar 15sp)\n")
        sb.append("• CHECKBOXES / TAREAS INTERACTIVAS:\n")
        sb.append("  - Usa '- [ ] Descripción de tarea pendiente' para tareas o items por hacer.\n")
        sb.append("  - Usa '- [x] Descripción de tarea realizada' para tareas ya completadas.\n")
        sb.append("• CAMBIO DE COLORES EN EL TEXTO:\n")
        sb.append("  - Aplica colores a frases o párrafos con la sintaxis: [color:Purple]Texto en color púrpura[/color], [color:Blue]Texto azul[/color], [color:Green]Texto verde[/color], [color:Red]Texto rojo[/color], [color:Amber]Texto ámbar[/color], o [color:#HEX]Texto[/color].\n")
        sb.append("  - O al inicio de una línea: [color:Blue] Párrafo destacado en azul.\n")
        sb.append("• ELEMENTOS GRÁFICOS Y LLAMATIVOS:\n")
        sb.append("  - Divisores de sección: usa '---', '--- [dotted]' para línea de puntos, o '--- [dashed]' para línea discontinua.\n")
        sb.append("  - Cuadros destacados (Callouts) con emojis y colores temáticos:\n")
        sb.append("    💡 [Purple] Ideas clave, recomendaciones y sugerencias creativas\n")
        sb.append("    📌 [Blue] Puntos importantes, notas de contexto o recordatorios\n")
        sb.append("    ⚠️ [Amber] Advertencias, riesgos o precauciones\n")
        sb.append("    🚀 [Green] Objetivos, planes de acción o metas futuras\n")
        sb.append("    ⭐ [Red] Conclusiones, hitos o datos esenciales\n")
        sb.append("  - Bloques de código con resaltado y lenguaje: ```kotlin o ```python o ```sql o ```json seguido de código y cerrado con ```.\n")
        sb.append("  - Tablas comparativas organizadas con Markdown: | Columna 1 | Columna 2 | con filas y separadores |---|---|\n")
        sb.append("  - Citas reflexivas o frases destacadas con '> Texto de la cita'.\n")
        sb.append("• ESPACIADOS Y LEGIBILIDAD:\n")
        sb.append("  - Inserta líneas en blanco entre secciones y bloques para dar espaciado limpio y agradable a la vista.\n\n")

        sb.append("REGLA: Responde siempre en español con calidez y precisión. Si te pidieron análisis o conversación, explica el tema y responde con claridad; si te pidieron gestionar el espacio de trabajo, usa los comandos.")
        return sb.toString()
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

    suspend fun sendMessage(
        message: String,
        imageBase64: String? = null,
        mimeType: String? = null,
        history: List<Pair<String, String>> = emptyList()
    ): String? {
        val noteId = _selectedNote.value?.id
        val agentPrompt = buildAgentSystemPrompt()
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

        val isDocumentSummary = message.contains("Instrucción de resumen", ignoreCase = true) ||
                                message.contains("Archivo Adjunto", ignoreCase = true) ||
                                (message.contains("resum", ignoreCase = true) && message.contains("Contexto"))

        val effectiveSystemPrompt = if (isDocumentSummary) {
            _chatbotStatusText.value = "📄 Aura está analizando el documento..."
            """
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
            """.trimIndent()
        } else {
            finalSystemPrompt
        }

        // Safely limit user prompt message length to avoid overflowing context for local model
        val maxLen = if (isDocumentSummary) 3200 else 5500
        val safeMessage = if (message.length > maxLen) message.take(maxLen) + "\n...[Contenido recortado para análisis global]" else message

        val rawResponse = localLlm.generateResponse(
            prompt = safeMessage + imageDisclaimer,
            systemPrompt = effectiveSystemPrompt,
            history = if (isDocumentSummary) emptyList() else history,
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
}
