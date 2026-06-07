package com.autobrowse.android.notes

import com.autobrowse.android.data.repository.AutobrowseRepository
import com.autobrowse.android.domain.model.Note
import com.autobrowse.android.domain.model.NoteBlock
import com.autobrowse.android.domain.model.NoteBlockType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class NotesManager(
    private val repository: AutobrowseRepository,
) {
    suspend fun prefetch(query: String): String = withContext(Dispatchers.IO) {
        val notes = repository.searchNotes(query, limit = 5)
        if (notes.isEmpty()) return@withContext ""
        buildString {
            appendLine("## Relevant User Notes")
            notes.forEach { note ->
                appendLine("- **${note.title.ifBlank { "Untitled" }}** (id=${note.id})")
                appendLine("  ${note.markdownBody.take(400).replace('\n', ' ')}")
            }
        }.trim()
    }

    suspend fun listRecent(limit: Int = 15): List<Note> =
        withContext(Dispatchers.IO) { repository.getRecentNotes(limit) }

    suspend fun read(id: String): Note? =
        withContext(Dispatchers.IO) { repository.getNote(id) }

    suspend fun search(query: String, limit: Int = 10): List<Note> =
        withContext(Dispatchers.IO) { repository.searchNotes(query, limit) }

    suspend fun create(
        title: String,
        body: String,
        sessionId: String? = null,
        tags: List<String> = emptyList(),
        append: Boolean = false,
        noteId: String? = null,
    ): Note = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = noteId?.let { repository.getNote(it) }
        val blocks = if (append && existing != null) {
            existing.blocks + NoteBlock(
                id = UUID.randomUUID().toString(),
                type = NoteBlockType.TEXT,
                markdown = body,
            )
        } else {
            listOf(
                NoteBlock(
                    id = UUID.randomUUID().toString(),
                    type = NoteBlockType.TEXT,
                    markdown = body,
                ),
            )
        }
        val note = Note(
            id = existing?.id ?: UUID.randomUUID().toString(),
            title = title.ifBlank { existing?.title ?: "Untitled" },
            blocks = blocks,
            sessionId = sessionId ?: existing?.sessionId,
            tags = if (tags.isNotEmpty()) tags else existing?.tags.orEmpty(),
            isPinned = existing?.isPinned ?: false,
            folder = existing?.folder ?: "Notes",
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        repository.saveNote(note)
    }

    suspend fun getPinnedSummary(limit: Int = 8): String = withContext(Dispatchers.IO) {
        val notes = repository.getRecentNotes(limit).filter { it.isPinned || it.title.isNotBlank() }
        if (notes.isEmpty()) return@withContext ""
        notes.joinToString("\n") { "- ${it.title} (${it.id})" }
    }
}