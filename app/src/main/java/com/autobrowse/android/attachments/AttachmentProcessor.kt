package com.autobrowse.android.attachments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.util.Base64
import com.autobrowse.android.domain.model.AttachmentPayload
import com.autobrowse.android.domain.model.AttachmentType
import com.autobrowse.android.domain.model.ProcessedAttachment
import com.autobrowse.android.domain.model.StoredAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class AttachmentProcessor(private val context: Context) {

    suspend fun processAll(attachments: List<StoredAttachment>): AttachmentPayload =
        withContext(Dispatchers.IO) {
            AttachmentPayload(attachments.map { process(it) })
        }

    suspend fun process(stored: StoredAttachment): ProcessedAttachment = withContext(Dispatchers.IO) {
        when (stored.type) {
            AttachmentType.IMAGE -> processImage(stored)
            AttachmentType.PDF -> processPdf(stored)
            AttachmentType.VIDEO -> processVideo(stored)
        }
    }

    private fun processImage(stored: StoredAttachment): ProcessedAttachment {
        val bytes = File(stored.localPath).readBytes()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val dataUrl = "data:${stored.mimeType};base64,$base64"
        return ProcessedAttachment(
            attachment = stored,
            description = "Image attached: ${stored.fileName} (${formatSize(stored.sizeBytes)})",
            imageBase64Parts = listOf(dataUrl),
        )
    }

    private fun processPdf(stored: StoredAttachment): ProcessedAttachment {
        val file = File(stored.localPath)
        val images = mutableListOf<String>()
        var pageCount = 0
        runCatching {
            context.contentResolver.openFileDescriptor(android.net.Uri.fromFile(file), "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    pageCount = renderer.pageCount
                    val pagesToRender = minOf(renderer.pageCount, 3)
                    for (i in 0 until pagesToRender) {
                        renderer.openPage(i).use { page ->
                            val bitmap = Bitmap.createBitmap(
                                page.width.coerceAtMost(1200),
                                page.height.coerceAtMost(1600),
                                Bitmap.Config.ARGB_8888,
                            )
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            images += bitmapToDataUrl(bitmap)
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
        return ProcessedAttachment(
            attachment = stored,
            description = "PDF attached: ${stored.fileName} — $pageCount page(s), ${formatSize(stored.sizeBytes)}. " +
                "${images.size} page preview(s) sent to vision model.",
            imageBase64Parts = images,
            textContent = "PDF document: ${stored.fileName}",
        )
    }

    private fun processVideo(stored: StoredAttachment): ProcessedAttachment {
        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        var width = 0
        var height = 0
        val thumbnails = mutableListOf<String>()
        var videoBase64: String? = null

        runCatching {
            retriever.setDataSource(stored.localPath)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0

            listOf(0L, durationMs / 2, (durationMs * 0.9).toLong()).distinct().forEach { timeUs ->
                retriever.getFrameAtTime(timeUs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { frame ->
                    thumbnails += bitmapToDataUrl(frame)
                    frame.recycle()
                }
            }

            if (stored.sizeBytes <= 8 * 1024 * 1024) {
                val bytes = File(stored.localPath).readBytes()
                videoBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        }.also { retriever.release() }

        val durationSec = durationMs / 1000.0
        return ProcessedAttachment(
            attachment = stored,
            description = buildString {
                append("Video attached: ${stored.fileName}")
                append(" — ${"%.1f".format(durationSec)}s, ${width}x$height, ${formatSize(stored.sizeBytes)}")
                if (videoBase64 != null) append(". Full video sent (model must support video input).")
                else append(". Key frames sent as images (video too large for inline encoding).")
            },
            imageBase64Parts = thumbnails,
            videoBase64 = videoBase64,
            textContent = "Video: ${stored.fileName}",
        )
    }

    private fun bitmapToDataUrl(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
    }
}