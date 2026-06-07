package com.autobrowse.android.data.local

data class ModelDownloadProgress(
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long? = null,
    val percent: Float = 0f,
    val message: String = "",
)