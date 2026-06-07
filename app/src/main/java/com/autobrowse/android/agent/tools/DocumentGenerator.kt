package com.autobrowse.android.agent.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class DocumentGenerator(private val context: Context) {

    suspend fun generatePdf(title: String, body: String): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "generated").also { it.mkdirs() }
        val file = File(dir, "${sanitize(title)}_${System.currentTimeMillis()}.pdf")
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 22f
            isFakeBoldText = true
        }
        val bodyPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
        }
        canvas.drawText(title, 40f, 60f, titlePaint)
        var y = 100f
        for (line in wrapText(body, 80)) {
            if (y > 800f) break
            canvas.drawText(line, 40f, y, bodyPaint)
            y += 18f
        }
        document.finishPage(page)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        file.absolutePath
    }

    suspend fun generateChart(title: String, labels: List<String>, values: List<Double>): String =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "generated").also { it.mkdirs() }
            val file = File(dir, "${sanitize(title)}_${System.currentTimeMillis()}.png")
            val width = 900
            val height = 600
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val titlePaint = Paint().apply { color = Color.BLACK; textSize = 36f; isFakeBoldText = true }
            val labelPaint = Paint().apply { color = Color.DKGRAY; textSize = 22f }
            val barPaint = Paint().apply { color = Color.parseColor("#4F46E5") }
            canvas.drawText(title, 40f, 50f, titlePaint)
            val maxValue = max(values.maxOrNull() ?: 1.0, 1.0)
            val barWidth = 60
            val gap = 40
            val baseY = 520f
            labels.forEachIndexed { index, label ->
                val value = values.getOrElse(index) { 0.0 }
                val barHeight = ((value / maxValue) * 380).toFloat()
                val left = 80f + index * (barWidth + gap)
                canvas.drawRect(left, baseY - barHeight, left + barWidth, baseY, barPaint)
                canvas.drawText(label.take(8), left, baseY + 30f, labelPaint)
                canvas.drawText("%.1f".format(value), left, baseY - barHeight - 10f, labelPaint)
            }
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            file.absolutePath
        }

    suspend fun extractPdfText(path: String): String = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext "File not found: $path"
        "PDF at ${file.absolutePath} (${file.length()} bytes). Use browser_vision or attached page images for OCR."
    }

    private fun sanitize(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "doc" }

    private fun wrapText(text: String, maxChars: Int): List<String> {
        val words = text.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            if (current.length + word.length + 1 > maxChars) {
                lines += current.toString()
                current = StringBuilder()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }
}