package com.autobrowse.android.attachments

import android.content.Context
import android.net.Uri
import com.autobrowse.android.domain.model.PendingAttachment
import com.autobrowse.android.domain.model.StoredAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class AttachmentStore(context: Context) {
    private val attachmentsDir = File(context.filesDir, "attachments").apply { mkdirs() }

    suspend fun persist(context: Context, pending: PendingAttachment): StoredAttachment =
        withContext(Dispatchers.IO) {
            val extension = pending.fileName.substringAfterLast('.', "bin")
            val id = pending.id.ifBlank { UUID.randomUUID().toString() }
            val dest = File(attachmentsDir, "$id.$extension")
            context.contentResolver.openInputStream(pending.uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Cannot read attachment: ${pending.fileName}")
            StoredAttachment(
                id = id,
                type = pending.type,
                fileName = pending.fileName,
                localPath = dest.absolutePath,
                mimeType = pending.mimeType,
                sizeBytes = dest.length(),
            )
        }

    fun resolveUri(stored: StoredAttachment): Uri = Uri.fromFile(File(stored.localPath))
}