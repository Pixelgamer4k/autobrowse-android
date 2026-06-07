package com.autobrowse.android.agent.training

import com.autobrowse.android.agent.core.TaskPreprocessor
import com.autobrowse.android.data.remote.LlmApiService
import com.autobrowse.android.data.repository.AutobrowseRepository
import com.autobrowse.android.domain.model.AgentTurn
import com.autobrowse.android.skills.SkillStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PostTaskSkillLearner(
    private val skillStore: SkillStore,
    private val repository: AutobrowseRepository,
    private val llmApi: LlmApiService,
) {
    suspend fun learnFromTask(
        prompt: String,
        success: Boolean,
        turns: List<AgentTurn>,
        pageUrl: String?,
    ): Int = withContext(Dispatchers.IO) {
        if (!success || prompt.isBlank()) return@withContext 0

        val toolCalls = turns.flatMap { it.toolCalls }
        if (toolCalls.isEmpty()) return@withContext 0

        val toolSequence = toolCalls.map { it.name }
        val domain = inferDomain(prompt, pageUrl)
        val triggers = extractTriggers(prompt, domain)
        val skillName = resolveSkillName(prompt, domain, triggers)
        val description = "Learned playbook for: ${prompt.take(96)}"

        val heuristicBody = buildHeuristicPlaybook(prompt, toolCalls, turns, pageUrl, domain)
        val body = maybePolishWithLlm(heuristicBody, prompt, toolSequence)

        skillStore.upsertLearnedFromTask(
            name = skillName,
            description = description,
            triggers = triggers,
            playbookBody = body,
            prompt = prompt,
            toolSequence = toolSequence,
        )
        1
    }

    private suspend fun maybePolishWithLlm(
        heuristicBody: String,
        prompt: String,
        toolSequence: List<String>,
    ): String {
        val config = repository.getLlmConfig()
        if (config.apiKey.isBlank()) return heuristicBody

        return runCatching {
            llmApi.chat(
                config = config,
                systemPrompt = """
                    Compress a successful browser automation run into a reusable SKILL.md body (markdown only, no frontmatter).
                    Include: when to use, step-by-step tool sequence, pitfalls, success criteria. Max 400 words.
                """.trimIndent(),
                userPrompt = """
                    Task: $prompt
                    Tool sequence: ${toolSequence.joinToString(" → ")}
                    Draft:
                    $heuristicBody
                """.trimIndent(),
            ).trim().ifBlank { heuristicBody }
        }.getOrDefault(heuristicBody)
    }

    private fun buildHeuristicPlaybook(
        prompt: String,
        toolCalls: List<com.autobrowse.android.domain.model.ToolCall>,
        turns: List<AgentTurn>,
        pageUrl: String?,
        domain: String,
    ): String = buildString {
        appendLine("# Learned: $domain")
        appendLine()
        appendLine("## When to use")
        appendLine("Tasks similar to: \"$prompt\"")
        appendLine()
        appendLine("## Proven tool sequence")
        toolCalls.forEachIndexed { index, call ->
            val args = call.argumentsJson.take(120)
            appendLine("${index + 1}. `${call.name}` — $args")
        }
        appendLine()
        appendLine("## Success criteria")
        appendLine("- Completed in ${turns.size} iterations with ${toolCalls.size} tool calls")
        if (!pageUrl.isNullOrBlank()) appendLine("- Final page: $pageUrl")
        val query = TaskPreprocessor.extractSearchQuery(prompt)
        if (query != null) appendLine("- Search query \"$query\" visible in URL or snapshot text")
        appendLine()
        appendLine("## Pitfalls")
        appendLine("- Do not repeat failed typing-in-search-box loops — use browser_search")
        appendLine("- Always browser_wait after navigation before snapshot")
    }

    private fun resolveSkillName(prompt: String, domain: String, triggers: List<String>): String {
        val base = (triggers.firstOrNull() ?: domain).lowercase()
            .replace(Regex("""[^a-z0-9]+"""), "-")
            .trim('-')
            .take(32)
            .ifBlank { "task" }
        return "learned-$base"
    }

    private fun extractTriggers(prompt: String, domain: String): List<String> {
        val lower = prompt.lowercase()
        val triggers = mutableListOf<String>()
        if (domain != "general") triggers += domain
        listOf("youtube", "google", "search", "research", "login", "form", "amazon", "reddit")
            .forEach { keyword ->
                if (lower.contains(keyword)) triggers += keyword
            }
        TaskPreprocessor.extractSearchQuery(prompt)?.let { triggers += it.take(40) }
        return triggers.distinct().take(6)
    }

    private fun inferDomain(prompt: String, pageUrl: String?): String {
        val lower = (prompt + " " + (pageUrl ?: "")).lowercase()
        return when {
            lower.contains("youtube") || lower.contains("youtu.be") -> "youtube-search"
            lower.contains("google") && lower.contains("search") -> "google-search"
            lower.contains("search") || lower.contains("find") -> "site-search"
            lower.contains("research") || lower.contains("summar") -> "research"
            lower.contains("form") || lower.contains("fill") -> "form-fill"
            lower.contains("login") || lower.contains("sign in") -> "auth"
            else -> "general"
        }
    }
}