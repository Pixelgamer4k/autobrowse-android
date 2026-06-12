package com.autobrowse.android.vpc.provision

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.squareup.moshi.Moshi
import java.io.File

class RootfsProvisioner(private val context: Context) {
    private val moshi = Moshi.Builder().build()
    private val rootfsDir = File(context.filesDir, "vpc/rootfs")
    private val versionFile = File(rootfsDir, ".vpc_version")

    fun loadBundledManifest(): RootfsManifest? = runCatching {
        context.assets.open("vpc/manifest.json").use { input ->
            val json = input.bufferedReader().readText()
            moshi.adapter(RootfsManifest::class.java).fromJson(json)
        }
    }.getOrNull()

    fun isProvisioned(): Boolean = versionFile.exists() && rootfsDir.list()?.isNotEmpty() == true

    fun installedVersion(): String? = versionFile.takeIf { it.exists() }?.readText()?.trim()

    fun enqueueDownload() {
        val manifest = loadBundledManifest() ?: return
        val request = OneTimeWorkRequestBuilder<RootfsDownloadWorker>()
            .setInputData(
                workDataOf(
                    RootfsDownloadWorker.KEY_URL to manifest.downloadUrl,
                    RootfsDownloadWorker.KEY_SHA256 to manifest.sha256,
                    RootfsDownloadWorker.KEY_VERSION to manifest.version,
                ),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun markProvisioned(version: String) {
        rootfsDir.mkdirs()
        versionFile.writeText(version)
    }

    companion object {
        const val WORK_NAME = "vpc_rootfs_download"
    }
}