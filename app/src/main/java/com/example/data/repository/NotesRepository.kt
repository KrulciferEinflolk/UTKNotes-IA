package com.example.data.repository

import com.example.data.local.NotesDao
import com.example.data.model.BookEntity
import com.example.data.model.NoteEntity
import com.example.data.model.PageEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class NotesRepository(private val notesDao: NotesDao) {

    // --- BOOKS ---
    fun getBooksForUser(userEmail: String): Flow<List<BookEntity>> =
        notesDao.getAllBooksFlow(userEmail)

    suspend fun getAllBooksList(userEmail: String): List<BookEntity> =
        notesDao.getAllBooks(userEmail)

    suspend fun getBook(id: String): BookEntity? = notesDao.getBookById(id)

    suspend fun createBook(
        title: String,
        colorHex: String = "#907CFF",
        coverUri: String? = null,
        coverScale: Float = 1.0f,
        coverOffsetX: Float = 0.0f,
        coverOffsetY: Float = 0.0f,
        userEmail: String = "offline"
    ): BookEntity {
        val book = BookEntity(
            title = title,
            colorHex = colorHex,
            coverUri = coverUri,
            coverScale = coverScale,
            coverOffsetX = coverOffsetX,
            coverOffsetY = coverOffsetY,
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


    // --- PAGES ---
    fun getPagesForBook(bookId: String): Flow<List<PageEntity>> =
        notesDao.getPagesForBookFlow(bookId)

    suspend fun getPagesForBookList(bookId: String): List<PageEntity> =
        notesDao.getPagesForBook(bookId)

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
        userEmail: String = "offline"
    ): NoteEntity {
        val note = NoteEntity(
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
}
