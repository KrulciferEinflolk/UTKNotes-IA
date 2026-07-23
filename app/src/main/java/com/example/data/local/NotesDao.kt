package com.example.data.local

import androidx.room.*
import com.example.data.model.BookEntity
import com.example.data.model.NoteEntity
import com.example.data.model.PageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotesDao {

    // --- BOOKS ---
    @Query("SELECT * FROM books WHERE userEmail = :userEmail ORDER BY title ASC")
    fun getAllBooksFlow(userEmail: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE userEmail = :userEmail ORDER BY title ASC")
    suspend fun getAllBooks(userEmail: String): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Update
    suspend fun updateBook(book: BookEntity)

    @Delete
    suspend fun deleteBook(book: BookEntity)


    // --- PAGES ---
    @Query("SELECT * FROM pages WHERE bookId = :bookId ORDER BY orderIndex ASC, createdAt ASC")
    fun getPagesForBookFlow(bookId: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE bookId = :bookId ORDER BY orderIndex ASC, createdAt ASC")
    suspend fun getPagesForBook(bookId: String): List<PageEntity>

    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun getPageById(id: String): PageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: PageEntity)

    @Update
    suspend fun updatePage(page: PageEntity)

    @Delete
    suspend fun deletePage(page: PageEntity)


    // --- NOTES ---
    @Query("SELECT * FROM notes WHERE pageId = :pageId AND isDeleted = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getNotesForPageFlow(pageId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE pageId = :pageId AND isDeleted = 0 ORDER BY isPinned DESC, updatedAt DESC")
    suspend fun getNotesForPage(pageId: String): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE userEmail = :userEmail AND isDeleted = 0")
    suspend fun getAllNotes(userEmail: String): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    // Soft delete / sync query (allows marking as deleted so sync can run)
    @Query("UPDATE notes SET isDeleted = 1, isSynced = 0, updatedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteNote(id: String, timestamp: Long = System.currentTimeMillis())

    // --- SEARCH & AI ---
    @Query("""
        SELECT * FROM notes 
        WHERE userEmail = :userEmail AND isDeleted = 0 AND (title LIKE :query OR content LIKE :query OR tags LIKE :query)
        ORDER BY updatedAt DESC
    """)
    fun searchNotesFlow(query: String, userEmail: String): Flow<List<NoteEntity>>

    @Query("""
        SELECT * FROM notes 
        WHERE userEmail = :userEmail AND isDeleted = 0 AND (title LIKE :query OR content LIKE :query OR tags LIKE :query)
        ORDER BY updatedAt DESC
    """)
    suspend fun searchNotes(query: String, userEmail: String): List<NoteEntity>

    // Get all notes with active reminders
    @Query("SELECT * FROM notes WHERE userEmail = :userEmail AND isDeleted = 0 AND reminderTime IS NOT NULL AND reminderStatus = 'pending' ORDER BY reminderTime ASC")
    fun getPendingRemindersFlow(userEmail: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE userEmail = :userEmail AND isDeleted = 0 AND reminderTime IS NOT NULL AND reminderStatus = 'pending' ORDER BY reminderTime ASC")
    suspend fun getPendingReminders(userEmail: String): List<NoteEntity>


    // --- SYNC QUERIES ---
    @Query("SELECT * FROM books WHERE userEmail = :userEmail AND updatedAt > :lastSync")
    suspend fun getModifiedBooks(lastSync: Long, userEmail: String): List<BookEntity>

    @Query("SELECT * FROM pages WHERE userEmail = :userEmail AND updatedAt > :lastSync")
    suspend fun getModifiedPages(lastSync: Long, userEmail: String): List<PageEntity>

    @Query("SELECT * FROM notes WHERE userEmail = :userEmail AND updatedAt > :lastSync")
    suspend fun getModifiedNotes(lastSync: Long, userEmail: String): List<NoteEntity>

    // --- CHAT SESSIONS ---
    @Query("SELECT * FROM chat_sessions WHERE userEmail = :userEmail ORDER BY createdAt DESC")
    fun getAllChatSessionsFlow(userEmail: String): Flow<List<com.example.data.model.ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE userEmail = :userEmail ORDER BY createdAt DESC")
    suspend fun getAllChatSessions(userEmail: String): List<com.example.data.model.ChatSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatSession(session: com.example.data.model.ChatSessionEntity)

    @Delete
    suspend fun deleteChatSession(session: com.example.data.model.ChatSessionEntity)

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun getChatSessionById(id: String): com.example.data.model.ChatSessionEntity?

    // --- MIGRATION QUERIES ---
    @Query("UPDATE books SET userEmail = :newEmail WHERE userEmail = 'offline' OR userEmail = ''")
    suspend fun migrateOfflineBooks(newEmail: String)

    @Query("UPDATE pages SET userEmail = :newEmail WHERE userEmail = 'offline' OR userEmail = ''")
    suspend fun migrateOfflinePages(newEmail: String)

    @Query("UPDATE notes SET userEmail = :newEmail WHERE userEmail = 'offline' OR userEmail = ''")
    suspend fun migrateOfflineNotes(newEmail: String)

    @Query("UPDATE chat_sessions SET userEmail = :newEmail WHERE userEmail = 'offline' OR userEmail = ''")
    suspend fun migrateOfflineChatSessions(newEmail: String)
}

