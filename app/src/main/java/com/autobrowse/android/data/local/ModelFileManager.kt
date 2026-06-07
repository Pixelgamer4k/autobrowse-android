package com.autobrowse.android.data.local

import android.content.Context
import android.net.Uri
import com.autobrowse.android.domain.model.LocalLlmCatalog
import com.autobrowse.android.domain.model.LocalLlmModel
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

    suspend fun importModel(uri: Uri, model: LocalLlmModel): ModelPaths = withContext(Dispatchers.IO) {
        val info = LocalLlmCatalog.infoFor(model)
        val modelsDir = modelsDir()
        val destination = File(modelsDir, info.modelFileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not read the selected model file.")
        pathsFor(model).copy(modelPath = destination.absolutePath)
    }

    suspend fun downloadModel(
        model: LocalLlmModel,
        onProgress: (ModelDownloadProgress) -> Unit,
    ): ModelPaths = withContext(Dispatchers.IO) {
        val info = LocalLlmCatalog.infoFor(model)
        val paths = pathsFor(model)
        val modelFile = File(paths.modelPath)
        val mmprojFile = File(paths.mmprojPath)

        if (modelFile.exists() && modelFile.length() > 0 &&
            mmprojFile.exists() && mmprojFile.length() > 0
        ) {
            onProgress(
                ModelDownloadProgress(
                    bytesDownloaded = modelFile.length() + mmprojFile.length(),
                    totalBytes = modelFile.length() + mmprojFile.length(),
                    percent = 1f,
                    message = "Model already downloaded.",
                ),
            )
            return@withContext paths
        }

        val downloads = listOf(
            DownloadTarget("Language model (Q4)", info.modelDownloadUrl, modelFile),
            DownloadTarget("Vision projector", info.mmprojDownloadUrl, mmprojFile),
        )

        var totalDownloaded = 0L
        var totalBytes = 0L

        downloads.forEachIndexed { index, target ->
            val label = target.label
            val url = target.url
            val destination = target.destination
            if (destination.exists() && destination.length() > 0) {
                totalDownloaded += destination.length()
                totalBytes += destination.length()
                onProgress(
                    ModelDownloadProgress(
                        bytesDownloaded = totalDownloaded,
                        totalBytes = if (totalBytes > 0) totalBytes else null,
                        percent = if (totalBytes > 0) (totalDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f,
                        message = "${label}: already present",
                    ),
                )
                return@forEachIndexed
            }

            onProgress(
                ModelDownloadProgress(
                    bytesDownloaded = totalDownloaded,
                    message = "Downloading ${label.lowercase()} (${index + 1}/${downloads.size})…",
                ),
            )

            val downloaded = downloadFile(
                url = url,
                destination = destination,
                label = label,
                onProgress = { fileProgress ->
                    onProgress(
                        ModelDownloadProgress(
                            bytesDownloaded = totalDownloaded + fileProgress.bytesDownloaded,
                            totalBytes = fileProgress.totalBytes?.let { totalDownloaded + it },
                            percent = fileProgress.percent,
                            message = "${label}: ${fileProgress.message}",
                        ),
                    )
                },
            )
            totalDownloaded += downloaded
            totalBytes += downloaded
        }

        onProgress(
            ModelDownloadProgress(
                bytesDownloaded = totalDownloaded,
                totalBytes = totalDownloaded,
                percent = 1f,
                message = "Download complete.",
            ),
        )
        paths
    }

    private fun downloadFile(
        url: String,
        destination: File,
        label: String,
        onProgress: (ModelDownloadProgress) -> Unit,
    ): Long {
        val modelsDir = modelsDir()
        val partial = File(modelsDir, "${destination.name}.partial")
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

    fun pathsFor(model: LocalLlmModel): ModelPaths {
        val info = LocalLlmCatalog.infoFor(model)
        val dir = modelsDir()
        return ModelPaths(
            modelPath = File(dir, info.modelFileName).absolutePath,
            mmprojPath = File(dir, info.mmprojFileName).absolutePath,
        )
    }

    fun isModelDownloaded(model: LocalLlmModel): Boolean {
        val paths = pathsFor(model)
        return modelFileExists(paths.modelPath) && modelFileExists(paths.mmprojPath)
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

data class ModelPaths(
    val modelPath: String,
    val mmprojPath: String,
)

private data class DownloadTarget(
    val label: String,
    val url: String,
    val destination: File,
)