package com.autobrowse.android.agent.tools

import com.autobrowse.android.notes.NotesManager

class NotesListTool(
    private val notesManager: NotesManager,
) : AgentTool {
    override val name = "notes_list"
    override val description = "List the user's saved Notes (titles and ids). Use before notes_read."
    override val parametersJson = """
        {"type":"object","properties":{"limit":{"type":"integer"}}}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val limit = args["limit"]?.toString()?.toIntOrNull()?.coerceIn(1, 30) ?: 12
        val notes = notesManager.listRecent(limit)
        if (notes.isEmpty()) return ToolExecutionResult("No notes yet. Suggest creating one with notes_create when the user shares important information.")
        val formatted = notes.joinToString("\n") { note ->
            val pin = if (note.isPinned) "📌 " else ""
            "$pin${note.title.ifBlank { "Untitled" }} [id=${note.id}] — ${note.markdownBody.take(80).replace('\n', ' ')}"
        }
        return ToolExecutionResult(formatted)
    }
}

class NotesSearchTool(
    private val notesManager: NotesManager,
) : AgentTool {
    override val name = "notes_search"
    override val description = "Full-text search across user Notes."
    override val parametersJson = """
        {"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val query = args["query"]?.toString().orEmpty()
        if (query.isBlank()) return ToolExecutionResult("query is required", success = false)
        val notes = notesManager.search(query)
        if (notes.isEmpty()) return ToolExecutionResult("No notes matched: $query")
        val formatted = notes.joinToString("\n\n") { note ->
            "**${note.title}** (id=${note.id})\n${note.markdownBody.take(600)}"
        }
        return ToolExecutionResult(formatted)
    }
}

class NotesReadTool(
    private val notesManager: NotesManager,
) : AgentTool {
    override val name = "notes_read"
    override val description = "Read a full note by id."
    override val parametersJson = """
        {"type":"object","properties":{"id":{"type":"string"}},"required":["id"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val id = args["id"]?.toString().orEmpty()
        if (id.isBlank()) return ToolExecutionResult("id is required", success = false)
        val note = notesManager.read(id)
            ?: return ToolExecutionResult("Note not found: $id", success = false)
        return ToolExecutionResult(
            buildString {
                appendLine("# ${note.title}")
                if (note.tags.isNotEmpty()) appendLine("Tags: ${note.tags.joinToString(", ")}")
                appendLine()
                append(note.markdownBody)
            },
        )
    }
}

class NotesCreateTool(
    private val notesManager: NotesManager,
) : AgentTool {
    override val name = "notes_create"
    override val description = "Create or append to a user Note. Use when the user wants to save research, summaries, or important info. Supports Markdown and LaTeX ($$...$$)."
    override val parametersJson = """
        {"type":"object","properties":{"title":{"type":"string"},"body":{"type":"string"},"tags":{"type":"array","items":{"type":"string"}},"append_to_id":{"type":"string"}},"required":["body"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val body = args["body"]?.toString().orEmpty()
        if (body.isBlank()) return ToolExecutionResult("body is required", success = false)
        val title = args["title"]?.toString().orEmpty()
        @Suppress("UNCHECKED_CAST")
        val tags = (args["tags"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
        val appendId = args["append_to_id"]?.toString()
        val note = notesManager.create(
            title = title,
            body = body,
            sessionId = context.sessionId,
            tags = tags,
            append = appendId != null,
            noteId = appendId,
        )
        return ToolExecutionResult("Saved note \"${note.title}\" (id=${note.id})")
    }
}

class NotesUpdateTool(
    private val notesManager: NotesManager,
) : AgentTool {
    override val name = "notes_update"
    override val description = "Append content to an existing note."
    override val parametersJson = """
        {"type":"object","properties":{"id":{"type":"string"},"body":{"type":"string"}},"required":["id","body"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val id = args["id"]?.toString().orEmpty()
        val body = args["body"]?.toString().orEmpty()
        if (id.isBlank() || body.isBlank()) {
            return ToolExecutionResult("id and body are required", success = false)
        }
        val note = notesManager.create(
            title = "",
            body = body,
            sessionId = context.sessionId,
            append = true,
            noteId = id,
        )
        return ToolExecutionResult("Updated note \"${note.title}\" (id=${note.id})")
    }
}