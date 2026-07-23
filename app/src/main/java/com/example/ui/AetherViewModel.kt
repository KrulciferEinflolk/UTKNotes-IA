package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.BookEntity
import com.example.data.model.NoteEntity
import com.example.data.model.PageEntity
import com.example.data.remote.DriveSyncManager
import com.example.data.remote.GeminiService
import com.example.data.remote.SyncState
import com.example.data.repository.NotesRepository
import com.example.ui.reminders.NotificationHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class AetherViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = NotesRepository(database.notesDao())
    val syncManager = DriveSyncManager(application, database)
    private val geminiService = GeminiService()

    init {
        geminiService.customApiKey = syncManager.geminiApiKey
    }
    
    fun updateGeminiApiKey(key: String?) {
        syncManager.geminiApiKey = key
        geminiService.customApiKey = key
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

    fun openChatbot(preAttachedNote: NoteEntity? = null, preAttachedText: String? = null) {
        _chatbotPreAttachedNote.value = preAttachedNote
        _chatbotPreAttachedText.value = preAttachedText
        _showChatbot.value = true
    }

    fun closeChatbot() {
        _showChatbot.value = false
        _chatbotPreAttachedNote.value = null
        _chatbotPreAttachedText.value = null
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
                geminiService.customApiKey = syncManager.geminiApiKey
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
        coverOffsetY: Float = 0.0f
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
            repository.deleteBook(book)
            if (_selectedBook.value?.id == book.id) {
                val remaining = repository.getAllBooksList(currentEmail.value)
                val nextBook = remaining.firstOrNull()
                _selectedBook.value = nextBook
                _selectedPage.value = null
                _selectedNote.value = null
                nextBook?.let { b ->
                    val pagesList = repository.getPagesForBookList(b.id)
                    if (pagesList.isEmpty()) {
                        val newPage = repository.createPage(b.id, "Notas", userEmail = currentEmail.value)
                        _selectedPage.value = newPage
                    } else {
                        _selectedPage.value = pagesList.firstOrNull()
                    }
                }
            }
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
                    "Agrega etiquetas como 'Estudio' o 'Trabajo' para organizar tus notas."
                )
                _aiLoading.value = false
                return@launch
            }

            val summary = currentNotes.joinToString("\n\n") { "Título: ${it.title}\nContenido: ${it.content}" }
            val suggestions = geminiService.generateSuggestions(summary)
            _aiSuggestions.value = suggestions.ifEmpty {
                listOf(
                    "Sintetiza tus notas actuales en un mapa mental.",
                    "Crea un recordatorio para revisar tus notas al final del día.",
                    "Agrega más detalles sobre las ideas registradas."
                )
            }
            _aiLoading.value = false
        }
    }

    fun applyAiModificationToNote(note: NoteEntity, instruction: String) {
        viewModelScope.launch {
            _aiLoading.value = true
            _aiMessage.value = "Pensando en modificaciones..."
            val result = geminiService.modifyNote(
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
                _aiMessage.value = "Nota modificada exitosamente con IA!"
            } else {
                _aiMessage.value = "No se pudieron aplicar cambios con IA."
            }
            _aiLoading.value = false
        }
    }

    fun createNoteWithAI(instruction: String) {
        val currentPage = _selectedPage.value ?: return
        viewModelScope.launch {
            _aiLoading.value = true
            _aiMessage.value = "Generando nueva nota..."
            val result = geminiService.modifyNote(
                title = "Idea Generada",
                content = "",
                instruction = "Crea una nota completa sobre: $instruction"
            )
            if (result != null) {
                val newNote = repository.createNote(
                    pageId = currentPage.id,
                    title = result.first,
                    content = result.second,
                    tags = "Generado, IA",
                    userEmail = currentEmail.value
                )
                _selectedNote.value = newNote
                _aiMessage.value = "Nueva nota generada con IA!"
            } else {
                _aiMessage.value = "Error al generar nota con IA."
            }
            _aiLoading.value = false
        }
    }

    private val moshi = com.squareup.moshi.Moshi.Builder()
        .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()

    fun parseAndApplyChatbotUpdates(response: String): String {
        var cleanResponse = response
        
        // 1. Check for UPDATE_NOTE
        val startTagUpdate = "[UPDATE_NOTE_START]"
        val endTagUpdate = "[UPDATE_NOTE_END]"
        if (cleanResponse.contains(startTagUpdate) && cleanResponse.contains(endTagUpdate)) {
            try {
                val startIndex = cleanResponse.indexOf(startTagUpdate)
                val endIndex = cleanResponse.indexOf(endTagUpdate)
                val blockContent = cleanResponse.substring(startIndex + startTagUpdate.length, endIndex).trim()
                
                // Parse ID
                val idRegex = Regex("^ID:\\s*(.*)", RegexOption.MULTILINE)
                val idMatch = idRegex.find(blockContent)
                val id = idMatch?.groupValues?.get(1)?.trim()
                
                // Parse TITLE
                val titleRegex = Regex("^TITLE:\\s*(.*)", RegexOption.MULTILINE)
                val titleMatch = titleRegex.find(blockContent)
                val title = titleMatch?.groupValues?.get(1)?.trim()
                
                // Parse CONTENT between CONTENT_START and CONTENT_END
                val contentStartMarker = "CONTENT_START"
                val contentEndMarker = "CONTENT_END"
                var noteContent: String? = null
                if (blockContent.contains(contentStartMarker) && blockContent.contains(contentEndMarker)) {
                    val cStartIdx = blockContent.indexOf(contentStartMarker)
                    val cEndIdx = blockContent.indexOf(contentEndMarker)
                    noteContent = blockContent.substring(cStartIdx + contentStartMarker.length, cEndIdx).trim()
                }
                
                if (!id.isNullOrEmpty() && !title.isNullOrEmpty() && noteContent != null) {
                    viewModelScope.launch {
                        val existingNote = repository.getNote(id)
                        if (existingNote != null) {
                            val parsedBlocks = com.example.parseTextContentToBlocks(noteContent)
                            val serializedContent = com.example.serializeBlocks(parsedBlocks)
                            val updatedNote = existingNote.copy(
                                title = title,
                                content = serializedContent,
                                updatedAt = System.currentTimeMillis(),
                                isSynced = false
                            )
                            repository.updateNote(updatedNote)
                            if (_selectedNote.value?.id == id) {
                                _selectedNote.value = updatedNote
                            }
                            if (syncManager.isConnected.value) {
                                triggerDriveSync()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AetherViewModel", "Failed to parse UPDATE_NOTE tag block", e)
            }
            
            // Clean response of the tag block
            val updateIdx = cleanResponse.indexOf(startTagUpdate)
            val endUpdateIdx = cleanResponse.indexOf(endTagUpdate)
            cleanResponse = cleanResponse.substring(0, updateIdx).trim() + 
                            "\n" + 
                            cleanResponse.substring(endUpdateIdx + endTagUpdate.length).trim()
        }

        // 2. Check for CREATE_NOTE
        val startTagCreate = "[CREATE_NOTE_START]"
        val endTagCreate = "[CREATE_NOTE_END]"
        if (cleanResponse.contains(startTagCreate) && cleanResponse.contains(endTagCreate)) {
            try {
                val startIndex = cleanResponse.indexOf(startTagCreate)
                val endIndex = cleanResponse.indexOf(endTagCreate)
                val blockContent = cleanResponse.substring(startIndex + startTagCreate.length, endIndex).trim()
                
                // Parse TITLE
                val titleRegex = Regex("^TITLE:\\s*(.*)", RegexOption.MULTILINE)
                val titleMatch = titleRegex.find(blockContent)
                val title = titleMatch?.groupValues?.get(1)?.trim()
                
                // Parse CONTENT between CONTENT_START and CONTENT_END
                val contentStartMarker = "CONTENT_START"
                val contentEndMarker = "CONTENT_END"
                var noteContent: String? = null
                if (blockContent.contains(contentStartMarker) && blockContent.contains(contentEndMarker)) {
                    val cStartIdx = blockContent.indexOf(contentStartMarker)
                    val cEndIdx = blockContent.indexOf(contentEndMarker)
                    noteContent = blockContent.substring(cStartIdx + contentStartMarker.length, cEndIdx).trim()
                }
                
                if (!title.isNullOrEmpty() && noteContent != null) {
                    val currentPage = _selectedPage.value
                    if (currentPage != null) {
                        viewModelScope.launch {
                            val parsedBlocks = com.example.parseTextContentToBlocks(noteContent)
                            val serializedContent = com.example.serializeBlocks(parsedBlocks)
                            val note = repository.createNote(
                                pageId = currentPage.id,
                                title = title,
                                content = serializedContent,
                                tags = "Generado, IA",
                                attachments = "[]",
                                reminderTime = null,
                                userEmail = currentEmail.value
                            )
                            _selectedNote.value = note
                            if (syncManager.isConnected.value) {
                                triggerDriveSync()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AetherViewModel", "Failed to parse CREATE_NOTE tag block", e)
            }
            
            // Clean response of the tag block
            val createIdx = cleanResponse.indexOf(startTagCreate)
            val endCreateIdx = cleanResponse.indexOf(endTagCreate)
            cleanResponse = cleanResponse.substring(0, createIdx).trim() + 
                            "\n" + 
                            cleanResponse.substring(endCreateIdx + endTagCreate.length).trim()
        }
        
        return cleanResponse.trim()
    }

    suspend fun sendMessage(message: String, imageBase64: String? = null, mimeType: String? = null): String? {
        val rawResponse = geminiService.sendMessage(message, imageBase64, mimeType) ?: return null
        return parseAndApplyChatbotUpdates(rawResponse)
    }
}
