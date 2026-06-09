package com.autobrowse.android.downloads

import android.content.Context
import android.webkit.CookieManager
import android.webkit.URLUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class DownloadItem(
    val id: String,
    val name: String,
    val path: String?,
    val url: String?,
    val mimeType: String,
    val sizeBytes: Long,
    val receivedBytes: Long = 0L,
    val modifiedAt: Long,
    val category: String,
    val status: DownloadStatus = DownloadStatus.COMPLETED,
    val tabId: String? = null,
    val errorMessage: String? = null,
) {
    val progress: Float
        get() = when {
            sizeBytes > 0L -> (receivedBytes.toFloat() / sizeBytes).coerceIn(0f, 1f)
            status == DownloadStatus.COMPLETED -> 1f
            else -> 0f
        }
}

class DownloadsManager(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val downloadDir = File(context.filesDir, "downloads").apply { mkdirs() }
    private val jobs = ConcurrentHashMap<String, Job>()
    private val cancelFlags = ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicBoolean>()

    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    init {
        scope.launch { refreshDiskIndex() }
    }

    fun enqueue(
        tabId: String,
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
    ) {
        val id = UUID.randomUUID().toString()
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val item = DownloadItem(
            id = id,
            name = fileName,
            path = null,
            url = url,
            mimeType = mimeType ?: "application/octet-stream",
            sizeBytes = contentLength.coerceAtLeast(0L),
            modifiedAt = System.currentTimeMillis(),
            category = "Browser",
            status = DownloadStatus.QUEUED,
            tabId = tabId,
        )
        _items.update { list -> listOf(item) + list }
        val cancelFlag = java.util.concurrent.atomic.AtomicBoolean(false)
        cancelFlags[id] = cancelFlag
        val job = scope.launch {
            runDownload(id, url, userAgent, fileName, contentLength, cancelFlag)
        }
        jobs[id] = job
        job.invokeOnCompletion {
            jobs.remove(id)
            cancelFlags.remove(id)
        }
    }

    fun cancel(id: String) {
        cancelFlags[id]?.set(true)
        jobs[id]?.cancel()
        _items.update { list ->
            list.map { item ->
                if (item.id == id && item.status in setOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING)) {
                    item.path?.let { runCatching { File(it).delete() } }
                    item.copy(status = DownloadStatus.CANCELLED, modifiedAt = System.currentTimeMillis())
                } else {
                    item
                }
            }
        }
    }

    fun delete(item: DownloadItem) {
        cancel(item.id)
        item.path?.let { runCatching { File(it).delete() } }
        _items.update { list -> list.filter { it.id != item.id } }
        scope.launch { refreshDiskIndex() }
    }

    suspend fun refresh() {
        refreshDiskIndex()
    }

    private suspend fun refreshDiskIndex() = withContext(Dispatchers.IO) {
        val dirs = listOf(
            "downloads" to "Browser",
            "screenshots" to "Screenshots",
            "generated" to "Generated",
            "attachments" to "Attachments",
        )
        val diskItems = dirs.flatMap { (folder, category) ->
            val dir = File(context.filesDir, folder)
            if (!dir.exists()) return@flatMap emptyList()
            dir.listFiles()?.mapNotNull { file ->
                if (!file.isFile) return@mapNotNull null
                DownloadItem(
                    id = file.absolutePath,
                    name = file.name,
                    path = file.absolutePath,
                    url = null,
                    mimeType = guessMime(file),
                    sizeBytes = file.length(),
                    modifiedAt = file.lastModified(),
                    category = category,
                    status = DownloadStatus.COMPLETED,
                )
            }.orEmpty()
        }
        val inMemory = _items.value.filter {
            it.status in ACTIVE_STATUSES || it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED
        }
        val knownPaths = inMemory.mapNotNull { it.path }.toSet()
        val merged = (inMemory + diskItems.filter { it.path !in knownPaths })
            .distinctBy { it.id }
            .sortedByDescending { it.modifiedAt }
        _items.value = merged
    }

    private suspend fun runDownload(
        id: String,
        url: String,
        userAgent: String,
        fileName: String,
        contentLength: Long,
        cancelFlag: java.util.concurrent.atomic.AtomicBoolean,
    ) = withContext(Dispatchers.IO) {
        updateItem(id) { it.copy(status = DownloadStatus.DOWNLOADING) }
        val safeName = fileName.replace(Regex("""[^\w.\-]"""), "_").take(120)
        val dest = File(downloadDir, "${id.take(8)}_$safeName")
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", userAgent)
                CookieManager.getInstance().getCookie(url)?.let { cookie ->
                    setRequestProperty("Cookie", cookie)
                }
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
            connection.connect()
            if (connection.responseCode !in 200..299) {
                error("HTTP ${connection.responseCode}")
            }
            val total = contentLength.takeIf { it > 0 } ?: connection.contentLengthLong
            connection.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var received = 0L
                    while (true) {
                        if (cancelFlag.get()) throw CancellationException("Cancelled")
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        received += read
                        if (received % (128 * 1024) < read) {
                            updateItem(id) {
                                it.copy(
                                    receivedBytes = received,
                                    sizeBytes = if (total > 0) total else received,
                                )
                            }
                        }
                    }
                }
            }
            connection.disconnect()
            updateItem(id) {
                it.copy(
                    status = DownloadStatus.COMPLETED,
                    path = dest.absolutePath,
                    receivedBytes = dest.length(),
                    sizeBytes = dest.length(),
                    modifiedAt = System.currentTimeMillis(),
                )
            }
        } catch (e: CancellationException) {
            runCatching { dest.delete() }
            updateItem(id) {
                it.copy(
                    status = DownloadStatus.CANCELLED,
                    modifiedAt = System.currentTimeMillis(),
                )
            }
        } catch (e: Exception) {
            runCatching { dest.delete() }
            updateItem(id) {
                it.copy(
                    status = DownloadStatus.FAILED,
                    errorMessage = e.message,
                    modifiedAt = System.currentTimeMillis(),
                )
            }
        }
    }

    private fun updateItem(id: String, transform: (DownloadItem) -> DownloadItem) {
        _items.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private fun guessMime(file: File): String = when (file.extension.lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        "json" -> "application/json"
        "mp4" -> "video/mp4"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }

    companion object {
        private val ACTIVE_STATUSES = setOf(
            DownloadStatus.QUEUED,
            DownloadStatus.DOWNLOADING,
        )
    }
}