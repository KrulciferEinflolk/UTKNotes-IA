package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.NotesDao
import com.example.data.model.BookEntity
import com.example.data.model.NoteEntity
import com.example.data.model.PageEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class NotesRepository(
    private val notesDao: NotesDao,
    private val database: AppDatabase? = null
) {

    // --- BOOKS ---
    fun getBooksForUser(userEmail: String): Flow<List<BookEntity>> =
        notesDao.getAllBooksFlow(userEmail)

    suspend fun getAllBooksList(userEmail: String): List<BookEntity> =
        notesDao.getAllBooks(userEmail)

    suspend fun getBook(id: String): BookEntity? = notesDao.getBookById(id)

    suspend fun createBook(
        title: String,
        colorHex: String = "#907CFF",
        textColorHex: String = "#FFFFFF",
        coverUri: String? = null,
        coverScale: Float = 1.0f,
        coverOffsetX: Float = 0.0f,
        coverOffsetY: Float = 0.0f,
        fontFamily: String = "Sans",
        userEmail: String = "offline"
    ): BookEntity {
        val book = BookEntity(
            title = title,
            colorHex = colorHex,
            textColorHex = textColorHex,
            coverUri = coverUri,
            coverScale = coverScale,
            coverOffsetX = coverOffsetX,
            coverOffsetY = coverOffsetY,
            fontFamily = fontFamily,
            userEmail = userEmail
        )
        notesDao.insertBook(book)
        return book
    }

    suspend fun updateBook(book: BookEntity) {
        val updated = book.copy(updatedAt = System.currentTimeMillis())
        notesDao.updateBook(updated)
    }

    suspend fun deleteBook(book: BookEntity) {
        notesDao.deleteBook(book)
    }

    suspend fun deleteBookSoft(id: String) {
        notesDao.softDeleteBook(id)
    }

    suspend fun deleteBookPermanent(book: BookEntity) {
        notesDao.deleteBook(book)
    }

    fun getDeletedBooks(userEmail: String): Flow<List<BookEntity>> =
        notesDao.getDeletedBooksFlow(userEmail)

    suspend fun restoreBook(id: String) {
        notesDao.restoreBook(id)
    }


    // --- PAGES ---
    fun getPagesForBook(bookId: String): Flow<List<PageEntity>> =
        notesDao.getPagesForBookFlow(bookId)

    suspend fun getPagesForBookList(bookId: String): List<PageEntity> =
        notesDao.getPagesForBook(bookId)

    fun getAllPagesForUser(userEmail: String): Flow<List<PageEntity>> =
        notesDao.getAllPagesFlow(userEmail)

    suspend fun getPage(id: String): PageEntity? = notesDao.getPageById(id)

    suspend fun createPage(bookId: String, title: String, orderIndex: Int = 0, userEmail: String = "offline"): PageEntity {
        val page = PageEntity(bookId = bookId, title = title, orderIndex = orderIndex, userEmail = userEmail)
        notesDao.insertPage(page)
        return page
    }

    suspend fun updatePage(page: PageEntity) {
        val updated = page.copy(updatedAt = System.currentTimeMillis())
        notesDao.updatePage(updated)
    }

    suspend fun deletePage(page: PageEntity) {
        notesDao.deletePage(page)
    }


    // --- NOTES ---
    fun getNotesForPage(pageId: String): Flow<List<NoteEntity>> =
        notesDao.getNotesForPageFlow(pageId)

    suspend fun getNote(id: String): NoteEntity? = notesDao.getNoteById(id)

    suspend fun createNote(
        pageId: String,
        title: String,
        content: String,
        tags: String = "",
        attachments: String = "[]",
        reminderTime: Long? = null,
        userEmail: String = "offline",
        id: String = UUID.randomUUID().toString()
    ): NoteEntity {
        val note = NoteEntity(
            id = id,
            pageId = pageId,
            title = title,
            content = content,
            tags = tags,
            attachments = attachments,
            reminderTime = reminderTime,
            reminderStatus = if (reminderTime != null) "pending" else "none",
            userEmail = userEmail
        )
        notesDao.insertNote(note)
        return note
    }

    suspend fun updateNote(note: NoteEntity) {
        val updated = note.copy(
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        )
        notesDao.updateNote(updated)
    }

    suspend fun deleteNoteSoft(id: String) {
        notesDao.softDeleteNote(id)
    }

    suspend fun deleteNotePermanent(note: NoteEntity) {
        notesDao.deleteNote(note)
    }

    fun getDeletedNotes(userEmail: String): Flow<List<NoteEntity>> =
        notesDao.getDeletedNotesFlow(userEmail)

    suspend fun restoreNote(id: String) {
        notesDao.restoreNote(id)
    }

    suspend fun emptyTrash(userEmail: String) {
        notesDao.emptyTrash(userEmail)
        notesDao.emptyBookTrash(userEmail)
    }


    // --- SEARCH & REMINDERS ---
    fun searchNotes(query: String, userEmail: String): Flow<List<NoteEntity>> {
        val formattedQuery = "%$query%"
        return notesDao.searchNotesFlow(formattedQuery, userEmail)
    }

    suspend fun searchNotesList(query: String, userEmail: String): List<NoteEntity> {
        val formattedQuery = "%$query%"
        return notesDao.searchNotes(formattedQuery, userEmail)
    }

    fun getPendingReminders(userEmail: String): Flow<List<NoteEntity>> =
        notesDao.getPendingRemindersFlow(userEmail)

    suspend fun getPendingRemindersList(userEmail: String): List<NoteEntity> =
        notesDao.getPendingReminders(userEmail)

    suspend fun completeReminder(noteId: String) {
        val note = notesDao.getNoteById(noteId) ?: return
        notesDao.updateNote(
            note.copy(
                reminderStatus = "completed",
                updatedAt = System.currentTimeMillis(),
                isSynced = false
            )
        )
    }

    // --- SYNC EXPORTS ---
    suspend fun getModifiedDataSince(lastSync: Long, userEmail: String): Triple<List<BookEntity>, List<PageEntity>, List<NoteEntity>> {
        val books = notesDao.getModifiedBooks(lastSync, userEmail)
        val pages = notesDao.getModifiedPages(lastSync, userEmail)
        val notes = notesDao.getModifiedNotes(lastSync, userEmail)
        return Triple(books, pages, notes)
    }

    suspend fun applySyncedData(
        books: List<BookEntity>,
        pages: List<PageEntity>,
        notes: List<NoteEntity>
    ) {
        for (book in books) {
            notesDao.insertBook(book)
        }
        for (page in pages) {
            notesDao.insertPage(page)
        }
        for (note in notes) {
            notesDao.insertNote(note)
        }
    }

    // --- CHAT SESSIONS ---
    fun getChatSessions(userEmail: String): Flow<List<com.example.data.model.ChatSessionEntity>> =
        notesDao.getAllChatSessionsFlow(userEmail)

    suspend fun insertChatSession(session: com.example.data.model.ChatSessionEntity) {
        notesDao.insertChatSession(session)
    }

    suspend fun deleteChatSession(session: com.example.data.model.ChatSessionEntity) {
        notesDao.deleteChatSession(session)
    }

    suspend fun getChatSession(id: String): com.example.data.model.ChatSessionEntity? {
        return notesDao.getChatSessionById(id)
    }

    // --- MULTI-ACCOUNT STORAGE & TRANSFERS ---
    private fun normalizeAccountKey(raw: String): String {
        val clean = raw.trim()
        return if (clean.isEmpty() || clean.equals("offline", ignoreCase = true)) {
            "offline"
        } else {
            clean.lowercase()
        }
    }

    private suspend fun <T> runInTransactionIfPossible(block: suspend () -> T): T {
        val db = database
        return if (db != null) {
            db.withTransaction { block() }
        } else {
            block()
        }
    }

    private suspend fun resolveCanonicalTargetAccount(rawTarget: String): String {
        val trimmed = rawTarget.trim()
        if (trimmed.isEmpty() || trimmed.equals("offline", ignoreCase = true)) {
            return "offline"
        }
        val targetKey = trimmed.lowercase()
        val existingMatch = getDistinctAccountEmails().firstOrNull {
            normalizeAccountKey(it) == targetKey
        }
        return existingMatch ?: trimmed
    }

    suspend fun normalizeEmptyAccountRecords() {
        notesDao.normalizeEmptyBooksToOffline()
        notesDao.normalizeEmptyPagesToOffline()
        notesDao.normalizeEmptyNotesToOffline()
        notesDao.normalizeEmptyChatSessionsToOffline()
    }

    suspend fun getDistinctAccountEmails(): Set<String> {
        val accounts = linkedSetOf("offline")
        accounts.addAll(notesDao.getDistinctBookAccounts().filter { it.isNotBlank() })
        accounts.addAll(notesDao.getDistinctPageAccounts().filter { it.isNotBlank() })
        accounts.addAll(notesDao.getDistinctNoteAccounts().filter { it.isNotBlank() })
        accounts.addAll(notesDao.getDistinctChatAccounts().filter { it.isNotBlank() })
        return accounts
    }

    suspend fun transferBookToAccount(
        bookId: String,
        targetAccount: String,
        mode: com.example.data.local.settings.AccountTransferMode,
        memoryVault: com.example.data.local.memory.NoteMemoryVaultManager? = null
    ): Boolean {
        return transferBookWithResult(bookId, targetAccount, mode, memoryVault).isSuccess
    }

    suspend fun transferBookWithResult(
        bookId: String,
        targetAccount: String,
        mode: com.example.data.local.settings.AccountTransferMode,
        memoryVault: com.example.data.local.memory.NoteMemoryVaultManager? = null
    ): Result<String> {
        return try {
            val resolvedTarget = resolveCanonicalTargetAccount(targetAccount)
            val book = notesDao.getBookById(bookId)
                ?: return Result.failure(IllegalArgumentException("No se encontró el libro seleccionado."))
            if (normalizeAccountKey(book.userEmail) == normalizeAccountKey(resolvedTarget)) {
                return Result.failure(IllegalArgumentException("La cuenta destino coincide con el origen del libro."))
            }

            val now = System.currentTimeMillis()
            val clonedVaultPairs = mutableListOf<Triple<NoteEntity, String, String>>() // (origNote, newNoteId, newPageId)

            runInTransactionIfPossible {
                val pages = notesDao.getPagesForBook(bookId)
                if (mode == com.example.data.local.settings.AccountTransferMode.MOVE) {
                    notesDao.reassignBookOwner(bookId, resolvedTarget, now)
                    notesDao.reassignPagesOwnerForBook(bookId, resolvedTarget, now)
                    notesDao.reassignNotesOwnerForBook(bookId, resolvedTarget, now)
                } else {
                    val newBookId = UUID.randomUUID().toString()
                    notesDao.insertBook(
                        book.copy(
                            id = newBookId,
                            userEmail = resolvedTarget,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                    for (page in pages) {
                        val newPageId = UUID.randomUUID().toString()
                        notesDao.insertPage(
                            page.copy(
                                id = newPageId,
                                bookId = newBookId,
                                userEmail = resolvedTarget,
                                createdAt = now,
                                updatedAt = now
                            )
                        )
                        val pageNotes = notesDao.getNotesForPage(page.id)
                        for (note in pageNotes) {
                            val newNoteId = UUID.randomUUID().toString()
                            notesDao.insertNote(
                                note.copy(
                                    id = newNoteId,
                                    pageId = newPageId,
                                    userEmail = resolvedTarget,
                                    isSynced = false,
                                    createdAt = now,
                                    updatedAt = now
                                )
                            )
                            clonedVaultPairs.add(Triple(note, newNoteId, newPageId))
                        }
                    }
                }
            }

            for ((origNote, newNoteId, newPageId) in clonedVaultPairs) {
                runCatching {
                    memoryVault?.cloneNoteVault(
                        sourceNoteId = origNote.id,
                        targetNoteId = newNoteId,
                        targetTitle = origNote.title,
                        targetPageId = newPageId
                    )
                }
            }
            Result.success(resolvedTarget)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun transferNoteToAccount(
        noteId: String,
        targetAccount: String,
        mode: com.example.data.local.settings.AccountTransferMode,
        memoryVault: com.example.data.local.memory.NoteMemoryVaultManager? = null
    ): Boolean {
        return transferNoteWithResult(noteId, targetAccount, mode, memoryVault).isSuccess
    }

    suspend fun transferNoteWithResult(
        noteId: String,
        targetAccount: String,
        mode: com.example.data.local.settings.AccountTransferMode,
        memoryVault: com.example.data.local.memory.NoteMemoryVaultManager? = null
    ): Result<String> {
        return try {
            val resolvedTarget = resolveCanonicalTargetAccount(targetAccount)
            val note = notesDao.getNoteById(noteId)
                ?: return Result.failure(IllegalArgumentException("No se encontró la nota seleccionada."))
            if (normalizeAccountKey(note.userEmail) == normalizeAccountKey(resolvedTarget)) {
                return Result.failure(IllegalArgumentException("La cuenta destino coincide con el origen de la nota."))
            }

            val now = System.currentTimeMillis()
            var clonedPair: Triple<NoteEntity, String, String>? = null

            runInTransactionIfPossible {
                val sourcePage = notesDao.getPageById(note.pageId)
                val sourceBook = sourcePage?.let { notesDao.getBookById(it.bookId) }

                val targetBooks = notesDao.getAllBooks(resolvedTarget)
                val preferredTitle = sourceBook?.title?.takeIf { it.isNotBlank() } ?: "Notas Transferidas"
                val targetBook = targetBooks.firstOrNull { it.title.equals(preferredTitle, ignoreCase = true) }
                    ?: createBook(
                        title = preferredTitle,
                        colorHex = sourceBook?.colorHex ?: "#907CFF",
                        textColorHex = sourceBook?.textColorHex ?: "#FFFFFF",
                        coverUri = sourceBook?.coverUri,
                        coverScale = sourceBook?.coverScale ?: 1.0f,
                        coverOffsetX = sourceBook?.coverOffsetX ?: 0.0f,
                        coverOffsetY = sourceBook?.coverOffsetY ?: 0.0f,
                        fontFamily = sourceBook?.fontFamily ?: "Sans",
                        userEmail = resolvedTarget
                    )

                val targetPages = notesDao.getPagesForBook(targetBook.id)
                val preferredPageTitle = sourcePage?.title?.takeIf { it.isNotBlank() } ?: "General"
                val targetPage = targetPages.firstOrNull { it.title.equals(preferredPageTitle, ignoreCase = true) }
                    ?: targetPages.firstOrNull()
                    ?: createPage(
                        bookId = targetBook.id,
                        title = preferredPageTitle,
                        orderIndex = 0,
                        userEmail = resolvedTarget
                    )

                if (mode == com.example.data.local.settings.AccountTransferMode.MOVE) {
                    notesDao.moveNoteToPageAndAccount(
                        noteId = noteId,
                        targetPageId = targetPage.id,
                        targetEmail = resolvedTarget,
                        timestamp = now
                    )
                } else {
                    val newNoteId = UUID.randomUUID().toString()
                    notesDao.insertNote(
                        note.copy(
                            id = newNoteId,
                            pageId = targetPage.id,
                            userEmail = resolvedTarget,
                            isSynced = false,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                    clonedPair = Triple(note, newNoteId, targetPage.id)
                }
            }

            clonedPair?.let { (origNote, newNoteId, targetPageId) ->
                runCatching {
                    memoryVault?.cloneNoteVault(
                        sourceNoteId = origNote.id,
                        targetNoteId = newNoteId,
                        targetTitle = origNote.title,
                        targetPageId = targetPageId
                    )
                }
            }
            Result.success(resolvedTarget)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
