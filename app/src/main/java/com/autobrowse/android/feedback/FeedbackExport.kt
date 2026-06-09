package com.autobrowse.android.feedback

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@JsonClass(generateAdapter = true)
data class FeedbackBundle(
    val format: String = FORMAT,
    val version: Int = VERSION,
    @Json(name = "exported_at") val exportedAt: Long = System.currentTimeMillis(),
    val entries: List<ExportedFeedbackEntry> = emptyList(),
) {
    companion object {
        const val FORMAT = "autobrowse-feedback"
        const val VERSION = 1
    }
}

@JsonClass(generateAdapter = true)
data class ExportedFeedbackEntry(
    val id: String,
    val content: String,
    val category: String,
    val tags: String = "",
    @Json(name = "priority_score") val priorityScore: Int = 0,
    val upvotes: Int = 0,
    val downvotes: Int = 0,
    @Json(name = "session_id") val sessionId: String? = null,
    val source: String = "user",
    @Json(name = "created_at") val createdAt: Long,
    @Json(name = "updated_at") val updatedAt: Long,
)

object FeedbackSerializer {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(FeedbackBundle::class.java)

    fun toJson(bundle: FeedbackBundle): String =
        adapter.toJson(bundle) ?: """{"format":"${FeedbackBundle.FORMAT}","version":1,"entries":[]}"""

    fun fromJson(json: String): FeedbackBundle {
        val bundle = adapter.fromJson(json)
            ?: throw IllegalArgumentException("Invalid feedback export file.")
        require(bundle.format == FeedbackBundle.FORMAT) {
            "Unsupported export format: ${bundle.format}"
        }
        require(bundle.version == FeedbackBundle.VERSION) {
            "Unsupported export version: ${bundle.version}"
        }
        return bundle
    }
}