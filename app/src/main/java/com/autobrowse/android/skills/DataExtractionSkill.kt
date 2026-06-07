package com.autobrowse.android.skills

import com.autobrowse.android.data.remote.LlmApiService
import com.autobrowse.android.data.repository.AutobrowseRepository
import com.autobrowse.android.domain.model.AgentAction
import com.autobrowse.android.domain.model.SkillType

class DataExtractionSkill(
    private val llmApi: LlmApiService,
    private val repository: AutobrowseRepository,
) : Skill {
    override val type = SkillType.DATA_EXTRACTION
    override val displayName = "Data Extraction"
    override val description = "Extract structured data from the current page or fetched content."

    override suspend fun execute(context: SkillContext): SkillResult {
        val source = context.pageText ?: context.pageHtml ?: ""
        if (source.isBlank()) {
            return SkillResult(success = false, output = "", error = "No page content available to extract.")
        }

        val config = repository.getLlmConfig()
        val systemPrompt = """
            You are a data extraction agent. Extract structured key-value data from the provided content.
            Return JSON only with fields: title, summary, key_points (array), entities (array).
            User memory context:
            ${context.memoryContext}
        """.trimIndent()

        return runCatching {
            val result = llmApi.chat(
                config = config,
                systemPrompt = systemPrompt,
                userPrompt = "Extract data for: ${context.userPrompt}\n\nContent:\n${source.take(12000)}",
            )
            SkillResult(
                success = true,
                output = result,
                extractedData = mapOf("raw_extraction" to result),
                actions = listOf(
                    AgentAction(type = "extract", reasoning = "Structured data extracted via LLM."),
                ),
            )
        }.getOrElse { error ->
            SkillResult(success = false, output = "", error = error.message)
        }
    }
}