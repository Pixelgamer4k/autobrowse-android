package com.autobrowse.android.domain.model

import android.net.Uri

enum class AttachmentType {
    IMAGE,
    PDF,
    VIDEO,
}

data class PendingAttachment(
    val id: String,
    val uri: Uri,
    val type: AttachmentType,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long = 0L,
)

data class StoredAttachment(
    val id: String,
    val type: AttachmentType,
    val fileName: String,
    val localPath: String,
    val mimeType: String,
    val sizeBytes: Long = 0L,
)

data class ProcessedAttachment(
    val attachment: StoredAttachment,
    val description: String,
    val imageBase64Parts: List<String> = emptyList(),
    val videoBase64: String? = null,
    val textContent: String? = null,
)

data class AttachmentPayload(
    val attachments: List<ProcessedAttachment> = emptyList(),
) {
    val hasVisionContent: Boolean
        get() = attachments.any {
            it.imageBase64Parts.isNotEmpty() || it.videoBase64 != null
        }

    fun buildContextBlock(): String = attachments.joinToString("\n\n") { it.description }

    fun merge(other: AttachmentPayload): AttachmentPayload =
        AttachmentPayload(attachments = attachments + other.attachments)

    companion object {
        fun fromVisionBase64(base64Parts: List<String>): AttachmentPayload {
            if (base64Parts.isEmpty()) return AttachmentPayload()
            return AttachmentPayload(
                attachments = base64Parts.mapIndexed { index, dataUrl ->
                    ProcessedAttachment(
                        attachment = StoredAttachment(
                            id = "vision_$index",
                            type = AttachmentType.IMAGE,
                            fileName = "vision_$index.jpg",
                            localPath = "",
                            mimeType = "image/jpeg",
                        ),
                        description = "Browser vision capture $index",
                        imageBase64Parts = listOf(
                            if (dataUrl.startsWith("data:")) dataUrl else "data:image/jpeg;base64,$dataUrl",
                        ),
                    )
                },
            )
        }
    }
}