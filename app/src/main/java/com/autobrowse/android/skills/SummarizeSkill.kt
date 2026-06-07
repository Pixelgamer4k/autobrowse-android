package com.autobrowse.android.skills

import com.autobrowse.android.data.remote.LlmApiService
import com.autobrowse.android.data.repository.AutobrowseRepository
import com.autobrowse.android.domain.model.AgentAction
import com.autobrowse.android.domain.model.SkillType

class SummarizeSkill(
    private val llmApi: LlmApiService,
    private val repository: AutobrowseRepository,
) : Skill {
    override val type = SkillType.SUMMARIZE
    override val displayName = "Summarize"
    override val description = "Summarize page content, research results, or long documents."

    override suspend fun execute(context: SkillContext): SkillResult {
        val content = context.pageText ?: context.pageHtml ?: ""
        if (content.isBlank()) {
            return SkillResult(success = false, output = "", error = "No content to summarize.")
        }

        val config = repository.getLlmConfig()
        val systemPrompt = """
            You are a concise research assistant. Summarize the content clearly with:
            - One-line headline
            - 3-5 bullet key points
            - Actionable takeaway
            User preferences:
            ${context.memoryContext}
        """.trimIndent()

        return runCatching {
            val summary = llmApi.chat(
                config = config,
                systemPrompt = systemPrompt,
                userPrompt = "Summarize for: ${context.userPrompt}\n\n${content.take(14000)}",
            )
            SkillResult(
                success = true,
                output = summary,
                extractedData = mapOf("summary" to summary),
                actions = listOf(AgentAction(type = "summarize", reasoning = "Content summarized.")),
            )
        }.getOrElse { error ->
            SkillResult(success = false, output = "", error = error.message)
        }
    }
}