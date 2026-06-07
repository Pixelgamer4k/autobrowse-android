package com.autobrowse.android.skills

import com.autobrowse.android.domain.model.AgentAction
import com.autobrowse.android.domain.model.SkillType

data class SkillContext(
    val sessionId: String,
    val pageUrl: String?,
    val pageHtml: String?,
    val pageText: String?,
    val userPrompt: String,
    val memoryContext: String,
)

data class SkillResult(
    val success: Boolean,
    val output: String,
    val extractedData: Map<String, String> = emptyMap(),
    val actions: List<AgentAction> = emptyList(),
    val error: String? = null,
)

interface Skill {
    val type: SkillType
    val displayName: String
    val description: String
    suspend fun execute(context: SkillContext): SkillResult
}