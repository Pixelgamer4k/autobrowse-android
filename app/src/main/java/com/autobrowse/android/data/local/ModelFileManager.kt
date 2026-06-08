package com.autobrowse.android.data.local

import android.content.Context
import android.net.Uri
import com.autobrowse.android.domain.model.LocalLlmCatalog
import com.autobrowse.android.domain.model.LocalLlmModel
import com.autobrowse.android.domain.model.LlmBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class ModelFileManager(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .build()

    suspend fun importModel(
        uri: Uri,
        model: LocalLlmModel,
        backend: LlmBackend = LlmBackend.GPU,
    ): String = withContext(Dispatchers.IO) {
        val artifact = LocalLlmCatalog.resolveArtifact(model, backend)
        val destination = File(modelsDir(), artifact.fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not read the selected model file.")
        destination.absolutePath
    }

    suspend fun downloadModel(
        model: LocalLlmModel,
        backend: LlmBackend,
        onProgress: (ModelDownloadProgress) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val info = LocalLlmCatalog.infoFor(model)
        val artifact = LocalLlmCatalog.resolveArtifact(model, backend)
        val destination = File(pathFor(model, backend))

        if (destination.exists() && destination.length() > 0) {
            onProgress(
                ModelDownloadProgress(
                    bytesDownloaded = destination.length(),
                    totalBytes = destination.length(),
                    percent = 1f,
                    message = "Model already downloaded.",
                ),
            )
            return@withContext destination.absolutePath
        }

        onProgress(
            ModelDownloadProgress(message = "Downloading ${info.displayName} (${artifact.fileName})…"),
        )

        downloadFile(
            url = artifact.downloadUrl,
            destination = destination,
            label = info.displayName,
            onProgress = onProgress,
        )

        onProgress(
            ModelDownloadProgress(
                bytesDownloaded = destination.length(),
                totalBytes = destination.length(),
                percent = 1f,
                message = "Download complete.",
            ),
        )
        destination.absolutePath
    }

    private fun downloadFile(
        url: String,
        destination: File,
        label: String,
        onProgress: (ModelDownloadProgress) -> Unit,
    ): Long {
        val partial = File(modelsDir(), "${destination.name}.partial")
        val resumeFrom = if (partial.exists()) partial.length() else 0L

        val requestBuilder = Request.Builder().url(url)
        if (resumeFrom > 0) {
            requestBuilder.header("Range", "bytes=$resumeFrom-")
        }
        val response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful && response.code != 206) {
            throw IllegalStateException("$label download failed: HTTP ${response.code}")
        }

        val body = response.body ?: throw IllegalStateException("Empty download response for $label")
        val contentLength = body.contentLength()
        val totalBytes = when {
            response.code == 206 -> resumeFrom + contentLength
            contentLength > 0 -> contentLength
            else -> null
        }

        java.io.FileOutputStream(partial, resumeFrom > 0).use { output ->
            body.byteStream().use { input ->
                val buffer = ByteArray(256 * 1024)
                var downloaded = resumeFrom
                var lastEmit = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (downloaded - lastEmit >= 512 * 1024) {
                        lastEmit = downloaded
                        val percent = totalBytes?.let { (downloaded.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
                        onProgress(
                            ModelDownloadProgress(
                                bytesDownloaded = downloaded,
                                totalBytes = totalBytes,
                                percent = percent,
                                message = formatProgress(downloaded, totalBytes),
                            ),
                        )
                    }
                }
            }
        }

        if (partial.exists()) {
            if (destination.exists()) destination.delete()
            partial.renameTo(destination)
        }
        return destination.length()
    }

    fun modelFileExists(path: String): Boolean = path.isNotBlank() && File(path).isFile

    fun pathFor(model: LocalLlmModel, backend: LlmBackend): String? =
        runCatching { LocalLlmCatalog.resolveArtifact(model, backend) }
            .getOrNull()
            ?.let { artifact -> File(modelsDir(), artifact.fileName).absolutePath }

    fun isModelDownloaded(model: LocalLlmModel, backend: LlmBackend): Boolean {
        val path = pathFor(model, backend) ?: return false
        return modelFileExists(path)
    }

    private fun modelsDir(): File = File(context.filesDir, "models").apply { mkdirs() }

    private fun formatProgress(downloaded: Long, total: Long?): String {
        val dlMb = downloaded / (1024.0 * 1024.0)
        return if (total != null && total > 0) {
            val totalMb = total / (1024.0 * 1024.0)
            String.format("%.1f / %.1f MB", dlMb, totalMb)
        } else {
            String.format("%.1f MB downloaded", dlMb)
        }
    }
}