package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val colorHex: String = "#907CFF",
    val textColorHex: String = "#FFFFFF",
    val coverUri: String? = null,
    val coverScale: Float = 1.0f,
    val coverOffsetX: Float = 0.0f,
    val coverOffsetY: Float = 0.0f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val userEmail: String = "offline"
)

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"])]
)
data class PageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val bookId: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val orderIndex: Int = 0,
    val userEmail: String = "offline"
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["pageId"])]
)
data class NoteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val pageId: String,
    val title: String,
    val content: String,
    val tags: String = "", // Comma-separated or JSON
    val attachments: String = "[]", // JSON string list of attachment names or paths
    val reminderTime: Long? = null, // Milliseconds timestamp, null if no reminder
    val reminderStatus: String = "none", // "none", "pending", "completed"
    val isPinned: Boolean = false,
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val userEmail: String = "offline"
)

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val messagesJson: String, // JSON string of message list
    val createdAt: Long = System.currentTimeMillis(),
    val userEmail: String = "offline"
)

