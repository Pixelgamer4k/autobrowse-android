package com.autobrowse.android.vpc.provision

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.autobrowse.android.AutobrowseApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

class RootfsDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val expectedSha = inputData.getString(KEY_SHA256).orEmpty()
        val version = inputData.getString(KEY_VERSION) ?: "unknown"
        val app = applicationContext as AutobrowseApplication
        val vpc = app.virtualPcManager

        try {
            vpc.onProvisioningProgress("download", 0f, "Connecting…")
            val archive = File(applicationContext.cacheDir, "rootfs.tar.zst")
            download(url, archive) { progress, detail ->
                vpc.onProvisioningProgress("download", progress, detail)
            }

            if (expectedSha.isNotBlank()) {
                val actual = archive.sha256Hex()
                if (!actual.equals(expectedSha, ignoreCase = true)) {
                    vpc.onProvisioningFailed("Checksum mismatch")
                    return@withContext Result.failure()
                }
            }

            vpc.onProvisioningProgress("extract", 0f, "Unpacking Ubuntu rootfs…")
            val rootfsDir = File(applicationContext.filesDir, "vpc/rootfs")
            TarZstExtractor(rootfsDir) { progress, detail ->
                vpc.onProvisioningProgress("extract", progress, detail)
            }.extract(archive.inputStream(), archive.length())

            app.rootfsProvisioner.markProvisioned(version)
            archive.delete()
            vpc.onProvisioningComplete(version)
            Result.success()
        } catch (e: Exception) {
            vpc.onProvisioningFailed(e.message ?: "Provisioning failed")
            Result.failure()
        }
    }

    private fun download(url: String, dest: File, onProgress: (Float, String) -> Unit) {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("Empty body")
            val total = body.contentLength().coerceAtLeast(1L)
            dest.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                var readTotal = 0L
                body.byteStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        readTotal += read
                        onProgress(
                            readTotal.toFloat() / total,
                            "Downloading ${readTotal / (1024 * 1024)} / ${total / (1024 * 1024)} MB",
                        )
                    }
                }
            }
        }
    }

    private fun File.sha256Hex(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_SHA256 = "sha256"
        const val KEY_VERSION = "version"
    }
}