package com.autobrowse.android.domain.model

data class Note(
    val id: String,
    val title: String,
    val blocks: List<NoteBlock>,
    val sessionId: String? = null,
    val tags: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val folder: String = "Notes",
    val createdAt: Long,
    val updatedAt: Long,
) {
    val plainText: String
        get() = buildString {
            append(title)
            blocks.forEach { block ->
                when (block.type) {
                    NoteBlockType.TEXT -> append('\n').append(block.markdown)
                    NoteBlockType.IMAGE -> append('\n').append("[image: ${block.caption ?: block.localPath.orEmpty()}]")
                    NoteBlockType.DRAWING -> append('\n').append("[drawing]")
                }
            }
        }

    val markdownBody: String
        get() = blocks.joinToString("\n\n") { it.toMarkdown() }
}

enum class NoteBlockType {
    TEXT,
    IMAGE,
    DRAWING,
}

data class NoteTextStyle(
    val fontSizeSp: Float = 16f,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val textColor: String? = null,
    val highlightColor: String? = null,
    val alignment: String = "start",
    val fontFamily: String = "default",
)

data class NoteBlock(
    val id: String,
    val type: NoteBlockType,
    val markdown: String = "",
    val localPath: String? = null,
    val caption: String? = null,
    val style: NoteTextStyle? = null,
    val strokeJson: String? = null,
) {
    fun toMarkdown(): String = when (type) {
        NoteBlockType.TEXT -> markdown
        NoteBlockType.IMAGE -> {
            val path = localPath.orEmpty()
            val alt = caption ?: "image"
            "![$alt]($path)"
        }
        NoteBlockType.DRAWING -> {
            val path = localPath.orEmpty()
            "![drawing]($path)"
        }
    }
}

data class NoteListItem(
    val id: String,
    val title: String,
    val preview: String,
    val isPinned: Boolean,
    val folder: String,
    val updatedAt: Long,
    val tags: List<String>,
)

enum class MiniAppId {
    NOTES,
}