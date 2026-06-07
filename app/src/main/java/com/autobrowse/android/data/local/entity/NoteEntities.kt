package com.autobrowse.android.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val blocksJson: String,
    val plainText: String,
    val sessionId: String? = null,
    val tags: String = "",
    val isPinned: Boolean = false,
    val folder: String = "Notes",
    val createdAt: Long,
    val updatedAt: Long,
)

@Fts4(contentEntity = NoteEntity::class)
@Entity(tableName = "notes_fts")
data class NoteFtsEntity(
    val title: String,
    val plainText: String,
    val tags: String,
)