package com.autobrowse.android.data.local

import android.content.Context
import android.net.Uri
import com.autobrowse.android.domain.model.LocalLlmCatalog
import com.autobrowse.android.domain.model.LocalLlmModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ModelFileManager(private val context: Context) {
    suspend fun importModel(uri: Uri, model: LocalLlmModel): String = withContext(Dispatchers.IO) {
        val info = LocalLlmCatalog.infoFor(model)
        val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
        val destination = File(modelsDir, info.defaultFileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not read the selected model file.")
        destination.absolutePath
    }

    fun modelFileExists(path: String): Boolean = path.isNotBlank() && File(path).isFile
}