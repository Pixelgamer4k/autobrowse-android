package com.autobrowse.android.skills

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@JsonClass(generateAdapter = true)
data class LearnedSkillsBundle(
    val format: String = FORMAT,
    val version: Int = VERSION,
    @Json(name = "exported_at") val exportedAt: Long = System.currentTimeMillis(),
    val skills: List<ExportedLearnedSkill> = emptyList(),
) {
    companion object {
        const val FORMAT = "autobrowse-learned-skills"
        const val VERSION = 1
    }
}

@JsonClass(generateAdapter = true)
data class ExportedLearnedSkill(
    val name: String,
    val description: String = "",
    val version: String = "1.0.0",
    val triggers: List<String> = emptyList(),
    @Json(name = "learned_runs") val learnedRuns: Int = 0,
    @Json(name = "skill_md") val skillMd: String,
    val files: Map<String, String> = emptyMap(),
)

object LearnedSkillsSerializer {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(LearnedSkillsBundle::class.java)

    fun toJson(bundle: LearnedSkillsBundle): String =
        adapter.toJson(bundle) ?: """{"format":"${LearnedSkillsBundle.FORMAT}","version":1,"skills":[]}"""

    fun fromJson(json: String): LearnedSkillsBundle {
        val bundle = adapter.fromJson(json)
            ?: throw IllegalArgumentException("Invalid learned skills export file.")
        require(bundle.format == LearnedSkillsBundle.FORMAT) {
            "Unsupported export format: ${bundle.format}"
        }
        require(bundle.version == LearnedSkillsBundle.VERSION) {
            "Unsupported export version: ${bundle.version}"
        }
        return bundle
    }

    fun emptyBundleJson(): String = toJson(LearnedSkillsBundle())
}