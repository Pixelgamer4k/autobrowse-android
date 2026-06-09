package com.autobrowse.android.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Entity(tableName = "strategies")
data class StrategyEntity(
    @PrimaryKey val id: String,
    val domain: String,
    val heuristic: String,
    val successCount: Int,
    val failureCount: Int,
    val confidence: Float,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "feedback_entries")
data class FeedbackEntryEntity(
    @PrimaryKey val id: String,
    val content: String,
    val category: String,
    val tags: String = "",
    val priorityScore: Int = 0,
    val upvotes: Int = 0,
    val downvotes: Int = 0,
    val sessionId: String? = null,
    val source: String = "user",
    val deleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "trajectories")
data class TrajectoryEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val taskId: String,
    val prompt: String,
    val success: Boolean,
    val turnsJson: String,
    val reflection: String?,
    val createdAt: Long,
)

@Fts4(contentEntity = MemoryEntryEntity::class)
@Entity(tableName = "memory_fts")
data class MemoryFtsEntity(
    val key: String,
    val value: String,
    val category: String,
    val tags: String,
)