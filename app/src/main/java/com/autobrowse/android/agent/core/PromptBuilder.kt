package com.autobrowse.android.agent.core

import com.autobrowse.android.agent.memory.MemoryManager
import com.autobrowse.android.data.repository.AutobrowseRepository
import com.autobrowse.android.domain.model.LearnedStrategy
import com.autobrowse.android.skills.SkillStore

/**
 * Hermes-inspired tiered prompt assembly:
 * stable (identity + tools) → context (memory + strategies) → volatile (page + prefetch)
 */
class PromptBuilder(
    private val repository: AutobrowseRepository,
    private val memoryManager: MemoryManager,
    private val skillStore: SkillStore? = null,
) {
    suspend fun build(
        prefetchedMemory: String,
        strategies: List<LearnedStrategy>,
        pageUrl: String?,
        enabledToolNames: List<String>,
    ): String {
        val stable = buildStableTier(enabledToolNames)
        val context = buildContextTier(prefetchedMemory, strategies)
        val skills = buildSkillsTier()
        val volatile = buildVolatileTier(pageUrl)
        return listOf(stable, context, skills, volatile).filter { it.isNotBlank() }.joinToString("\n\n")
    }

    private fun buildStableTier(toolNames: List<String>): String = """
        # Autobrowse Agent
        You are Autobrowse — a Hermes-inspired, self-improving browser automation agent on Android.
        You browse, research, extract data, fill forms, run parallel tasks, generate PDFs/charts, and learn from every task.

        ## Operating Rules
        - Use tools to act; do not hallucinate page content or URLs.
        - Call browser_snapshot (interactive refs) before clicking; prefer @eN refs over CSS selectors.
        - Use browser_vision when layout is unclear; browser_click_xy for canvas/custom UIs.
        - Use browser_tab_open/switch/close for multi-tab workflows; run_parallel_tasks for independent prompts.
        - Use todo_write for 3+ step tasks; skill_view('autobrowse') for the core browsing playbook.
        - Use skill_creator / skill_manage to save successful workflows as reusable skills.
        - Use python_execute / execute_code for charts (matplotlib) and PDF generation.
        - Use memory_remember for durable facts; reflect after difficult tasks.
        - Be concise, secure, and never expose API keys.
        - Stop when the user's goal is achieved and respond with a clear summary.
        - Vision: user attachments and browser_vision screenshots are provided as images to cloud models.

        ## Available Tools
        ${toolNames.joinToString(", ")}
    """.trimIndent()

    private suspend fun buildSkillsTier(): String {
        val store = skillStore ?: return ""
        val skills = store.listSkills().take(12)
        if (skills.isEmpty()) return ""
        return buildString {
            appendLine("## Available Skills (use skill_view to load)")
            skills.forEach { appendLine("- ${it.name}: ${it.description}") }
        }.trim()
    }

    private suspend fun buildContextTier(
        prefetchedMemory: String,
        strategies: List<LearnedStrategy>,
    ): String = buildString {
        val userBlock = memoryManager.getUserProfileBlock()
        val memoryBlock = memoryManager.getMemoryBlock()
        if (userBlock.isNotBlank()) {
            appendLine(userBlock)
            appendLine()
        }
        if (memoryBlock.isNotBlank()) {
            appendLine(memoryBlock)
            appendLine()
        }
        if (prefetchedMemory.isNotBlank()) {
            appendLine("## Relevant Memories (prefetched)")
            appendLine(prefetchedMemory)
            appendLine()
        }
        if (strategies.isNotEmpty()) {
            appendLine("## Learned Strategies (self-improved)")
            strategies.forEach { appendLine("- [${it.domain}] ${it.heuristic} (confidence ${"%.0f".format(it.confidence * 100)}%)") }
        }
    }.trim()

    private fun buildVolatileTier(pageUrl: String?): String {
        if (pageUrl.isNullOrBlank()) return "## Current Page\nNo active page."
        return "## Current Page\nActive URL: $pageUrl"
    }
}