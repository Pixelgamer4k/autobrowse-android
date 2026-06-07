package com.autobrowse.android.agent.tools

import com.autobrowse.android.data.remote.LlmApiService
import com.autobrowse.android.data.repository.AutobrowseRepository
import com.autobrowse.android.domain.model.SkillType
import com.autobrowse.android.skills.SkillContext
import com.autobrowse.android.skills.SkillRegistry

class WebFetchTool(
    private val skillRegistry: SkillRegistry,
    private val repository: AutobrowseRepository,
) : AgentTool {
    override val name = "web_fetch"
    override val description = "Fetch a remote URL and return response content."
    override val parametersJson = """
        {"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val url = args["url"]?.toString().orEmpty()
        val enabled = repository.getEnabledSkills()
        if (enabled.isNotEmpty() && SkillType.WEB_REQUEST !in enabled) {
            return ToolExecutionResult("Web request skill disabled", success = false)
        }
        val skill = skillRegistry.getEnabledSkills(setOf(SkillType.WEB_REQUEST)).firstOrNull()
            ?: return ToolExecutionResult("Web request skill unavailable", success = false)
        val result = skill.execute(
            SkillContext(
                sessionId = context.sessionId,
                pageUrl = url,
                pageHtml = context.pageHtml,
                pageText = context.pageText,
                userPrompt = "fetch $url",
                memoryContext = repository.getMemoryContext(),
            ),
        )
        context.extractedData.putAll(result.extractedData)
        context.browserActions.addAll(result.actions)
        return ToolExecutionResult(result.output.ifBlank { result.error.orEmpty() }, success = result.success)
    }
}

class ExtractDataTool(
    private val skillRegistry: SkillRegistry,
    private val repository: AutobrowseRepository,
) : AgentTool {
    override val name = "extract_data"
    override val description = "Extract structured data from the current page or provided content."
    override val parametersJson = """
        {"type":"object","properties":{"instruction":{"type":"string"}},"required":["instruction"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val instruction = args["instruction"]?.toString().orEmpty()
        val enabled = repository.getEnabledSkills()
        if (enabled.isNotEmpty() && SkillType.DATA_EXTRACTION !in enabled) {
            return ToolExecutionResult("Data extraction skill disabled", success = false)
        }
        val skill = skillRegistry.getEnabledSkills(setOf(SkillType.DATA_EXTRACTION)).firstOrNull()
            ?: return ToolExecutionResult("Data extraction skill unavailable", success = false)
        val result = skill.execute(
            SkillContext(
                sessionId = context.sessionId,
                pageUrl = context.pageUrl,
                pageHtml = context.pageHtml,
                pageText = context.pageText,
                userPrompt = instruction,
                memoryContext = repository.getMemoryContext(),
            ),
        )
        context.extractedData.putAll(result.extractedData)
        return ToolExecutionResult(result.output.ifBlank { result.error.orEmpty() }, success = result.success)
    }
}

class SummarizeTool(
    private val skillRegistry: SkillRegistry,
    private val repository: AutobrowseRepository,
) : AgentTool {
    override val name = "summarize"
    override val description = "Summarize page content or research results."
    override val parametersJson = """
        {"type":"object","properties":{"instruction":{"type":"string"}},"required":["instruction"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val instruction = args["instruction"]?.toString().orEmpty()
        val enabled = repository.getEnabledSkills()
        if (enabled.isNotEmpty() && SkillType.SUMMARIZE !in enabled) {
            return ToolExecutionResult("Summarize skill disabled", success = false)
        }
        val skill = skillRegistry.getEnabledSkills(setOf(SkillType.SUMMARIZE)).firstOrNull()
            ?: return ToolExecutionResult("Summarize skill unavailable", success = false)
        val result = skill.execute(
            SkillContext(
                sessionId = context.sessionId,
                pageUrl = context.pageUrl,
                pageHtml = context.pageHtml,
                pageText = context.pageText,
                userPrompt = instruction,
                memoryContext = repository.getMemoryContext(),
            ),
        )
        context.extractedData.putAll(result.extractedData)
        return ToolExecutionResult(result.output.ifBlank { result.error.orEmpty() }, success = result.success)
    }
}

class ReflectTool(
    private val llmApi: LlmApiService,
    private val repository: AutobrowseRepository,
) : AgentTool {
    override val name = "reflect"
    override val description = "Analyze what worked or failed and propose a reusable heuristic for future tasks."
    override val parametersJson = """
        {"type":"object","properties":{"observation":{"type":"string"},"outcome":{"type":"string","enum":["success","failure"]}},"required":["observation","outcome"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val observation = args["observation"]?.toString().orEmpty()
        val outcome = args["outcome"]?.toString() ?: "success"
        val config = repository.getLlmConfig()
        val heuristic = llmApi.chat(
            config = config,
            systemPrompt = "Extract one concise, actionable browsing heuristic from the observation. One sentence only.",
            userPrompt = "Outcome: $outcome\nObservation: $observation",
        )
        context.extractedData["heuristic"] = heuristic
        return ToolExecutionResult("Heuristic: $heuristic")
    }
}