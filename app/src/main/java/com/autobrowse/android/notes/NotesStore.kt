package com.autobrowse.android.notes

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class NotesStore(context: Context) {
    private val notesDir = File(context.filesDir, "notes").apply { mkdirs() }
    private val imagesDir = File(notesDir, "images").apply { mkdirs() }
    private val drawingsDir = File(notesDir, "drawings").apply { mkdirs() }

    suspend fun persistImage(context: Context, uri: Uri, fileName: String): String =
        withContext(Dispatchers.IO) {
            val extension = fileName.substringAfterLast('.', "jpg")
            val dest = File(imagesDir, "${UUID.randomUUID()}.$extension")
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Cannot read image: $fileName")
            dest.absolutePath
        }

    suspend fun saveDrawing(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val dest = File(drawingsDir, "${UUID.randomUUID()}.png")
        dest.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        dest.absolutePath
    }

    fun imageUri(path: String): Uri = Uri.fromFile(File(path))
}