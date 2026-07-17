package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.BookEntity
import com.example.data.model.NoteEntity
import com.example.data.model.PageEntity

@Database(
    entities = [BookEntity::class, PageEntity::class, NoteEntity::class, com.example.data.model.ChatSessionEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notesDao(): NotesDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aether_notes_database"
                )
                .fallbackToDestructiveMigration() // Simple for rapid prototyping/sync
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
