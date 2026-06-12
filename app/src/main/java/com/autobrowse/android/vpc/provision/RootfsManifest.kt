package com.autobrowse.android.vpc.provision

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RootfsManifest(
    val version: String,
    @Json(name = "download_url") val downloadUrl: String,
    val sha256: String,
    @Json(name = "size_bytes") val sizeBytes: Long = 0L,
    @Json(name = "min_app_version") val minAppVersion: String? = null,
)