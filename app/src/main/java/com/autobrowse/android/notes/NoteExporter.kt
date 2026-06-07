package com.autobrowse.android.notes

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.autobrowse.android.domain.model.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteExporter(private val context: Context) {
    private val exportDir = File(context.cacheDir, "note_exports").apply { mkdirs() }

    suspend fun exportText(note: Note): Uri = withContext(Dispatchers.IO) {
        val file = File(exportDir, "${safeName(note.title)}.txt")
        file.writeText(buildString {
            appendLine(note.title.ifBlank { "Untitled" })
            appendLine("=".repeat(note.title.length.coerceAtLeast(8)))
            appendLine()
            append(note.markdownBody)
            if (note.tags.isNotEmpty()) {
                appendLine()
                appendLine()
                append("Tags: ").append(note.tags.joinToString(", "))
            }
        })
        fileProviderUri(file)
    }

    suspend fun exportMarkdown(note: Note): Uri = withContext(Dispatchers.IO) {
        val file = File(exportDir, "${safeName(note.title)}.md")
        file.writeText("# ${note.title.ifBlank { "Untitled" }}\n\n${note.markdownBody}")
        fileProviderUri(file)
    }

    suspend fun exportPdf(note: Note): Uri = withContext(Dispatchers.IO) {
        val file = File(exportDir, "${safeName(note.title)}.pdf")
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val titlePaint = Paint().apply {
            isAntiAlias = true
            textSize = 22f
            isFakeBoldText = true
        }
        val bodyPaint = Paint().apply {
            isAntiAlias = true
            textSize = 12f
        }
        var y = 48f
        canvas.drawText(note.title.ifBlank { "Untitled" }, 40f, y, titlePaint)
        y += 28f
        note.markdownBody.lines().forEach { line ->
            if (y > 800f) return@forEach
            canvas.drawText(line.take(90), 40f, y, bodyPaint)
            y += 16f
        }
        document.finishPage(page)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        fileProviderUri(file)
    }

    suspend fun exportImage(note: Note, width: Int = 1080, height: Int = 1920): Uri =
        withContext(Dispatchers.IO) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(0xFFFFF8E7.toInt())
            val titlePaint = Paint().apply {
                isAntiAlias = true
                textSize = 48f
                color = 0xFF1C1C1E.toInt()
                isFakeBoldText = true
            }
            val bodyPaint = Paint().apply {
                isAntiAlias = true
                textSize = 32f
                color = 0xFF3A3A3C.toInt()
            }
            var y = 120f
            canvas.drawText(note.title.ifBlank { "Untitled" }, 64f, y, titlePaint)
            y += 72f
            note.markdownBody.lines().forEach { line ->
                if (y > height - 80f) return@forEach
                canvas.drawText(line.take(42), 64f, y, bodyPaint)
                y += 44f
            }
            val file = File(exportDir, "${safeName(note.title)}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            fileProviderUri(file)
        }

    fun buildShareIntent(uri: Uri, mimeType: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    private fun fileProviderUri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun safeName(title: String): String {
        val base = title.ifBlank { "note" }
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "${base.ifBlank { "note" }}-$stamp"
    }
}