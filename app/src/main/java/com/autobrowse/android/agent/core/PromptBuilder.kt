package com.autobrowse.android.agent.core

import com.autobrowse.android.agent.memory.MemoryManager
import com.autobrowse.android.data.repository.AutobrowseRepository
import com.autobrowse.android.domain.model.LearnedStrategy

/**
 * Hermes-inspired tiered prompt assembly:
 * stable (identity + tools) → context (memory + strategies) → volatile (page + prefetch)
 */
class PromptBuilder(
    private val repository: AutobrowseRepository,
    private val memoryManager: MemoryManager,
) {
    suspend fun build(
        prefetchedMemory: String,
        strategies: List<LearnedStrategy>,
        pageUrl: String?,
        enabledToolNames: List<String>,
    ): String {
        val stable = buildStableTier(enabledToolNames)
        val context = buildContextTier(prefetchedMemory, strategies)
        val volatile = buildVolatileTier(pageUrl)
        return listOf(stable, context, volatile).filter { it.isNotBlank() }.joinToString("\n\n")
    }

    private fun buildStableTier(toolNames: List<String>): String = """
        # Autobrowse Agent
        You are Autobrowse — a self-improving browser automation agent inside an Android hybrid browser app.
        You browse, research, extract data, fill forms, summarize content, and learn from every task.

        ## Operating Rules
        - Use tools to act; do not hallucinate page content or URLs.
        - Call browser_snapshot before extract_data or summarize when page context is unknown.
        - Use memory_remember for durable user facts and preferences.
        - Use reflect after difficult tasks to capture reusable heuristics.
        - Be concise, secure, and never expose API keys.
        - Stop when the user's goal is achieved and respond with a clear summary.
        - When users attach images, PDFs, or videos, analyze them carefully. PDF pages and video key frames are provided as images.

        ## Available Tools
        ${toolNames.joinToString(", ")}
    """.trimIndent()

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