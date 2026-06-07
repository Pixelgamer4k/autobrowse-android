package com.autobrowse.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val lastActiveAt: Long,
    val isActive: Boolean,
    val parentSessionId: String? = null,
    val compressionSummary: String? = null,
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val metadataJson: String = "{}",
)

@Entity(tableName = "memory_entries")
data class MemoryEntryEntity(
    @PrimaryKey val id: String,
    val key: String,
    val value: String,
    val category: String,
    val importance: Int = 5,
    val tags: String = "",
    val source: String = "agent",
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "automation_tasks")
data class AutomationTaskEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val title: String,
    val description: String,
    val status: String,
    val progress: Float,
    val createdAt: Long,
    val skillType: String?,
)

@Entity(tableName = "browser_tabs")
data class BrowserTabEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val url: String,
    val title: String,
    val status: String,
    val isAgentControlled: Boolean,
    val zIndex: Int,
    val lastVisitedAt: Long,
    val offsetX: Float = 0.05f,
    val offsetY: Float = 0.05f,
    val widthFraction: Float = 0.68f,
    val heightFraction: Float = 0.58f,
    val windowState: String = "NORMAL",
    val savedOffsetX: Float? = null,
    val savedOffsetY: Float? = null,
    val savedWidthFraction: Float? = null,
    val savedHeightFraction: Float? = null,
    val desktopMode: Boolean = true,
)