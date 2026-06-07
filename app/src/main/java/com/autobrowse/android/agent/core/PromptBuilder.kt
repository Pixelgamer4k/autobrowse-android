package com.autobrowse.android.agent.core

import com.autobrowse.android.agent.memory.MemoryManager
import com.autobrowse.android.data.repository.AutobrowseRepository
import com.autobrowse.android.domain.model.LearnedStrategy
import com.autobrowse.android.skills.SkillStore

/**
 * Hermes-inspired tiered prompt assembly:
 * stable (identity + tools) → context (memory + strategies) → skills → volatile (page)
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
        userPrompt: String = "",
    ): String {
        val stable = buildStableTier(enabledToolNames)
        val searchPlaybook = buildSearchPlaybook()
        val context = buildContextTier(prefetchedMemory, strategies)
        val skills = buildSkillsTier(userPrompt)
        val volatile = buildVolatileTier(pageUrl)
        return listOf(stable, searchPlaybook, context, skills, volatile).filter { it.isNotBlank() }.joinToString("\n\n")
    }

    private fun buildStableTier(toolNames: List<String>): String = """
        # Autobrowse Agent
        You are Autobrowse — an expert browser automation agent on Android. Complete tasks in MINIMAL steps.

        ## Critical Rules (violations cause failure)
        1. **SEARCH = browser_search** — NEVER browser_type into search boxes on YouTube, Google, Bing, etc.
        2. **WAIT after navigate** — browser_wait(2000) or rely on auto-wait, then browser_snapshot
        3. **VERIFY then STOP** — if snapshot shows results, summarize and finish. Do NOT loop 10+ times.
        4. **Simple search = 3-5 tool calls max**: browser_search → browser_wait → browser_snapshot → respond
        5. Snapshot BEFORE click — use @eN refs from browser_snapshot, not guessed CSS selectors

        ## Tool Priority
        - Search: browser_search (site + query)
        - Navigate: browser_navigate + browser_wait
        - Click/type: browser_snapshot first → browser_click(browser_type) with refs
        - Stuck: browser_vision (cloud) or browser_search with direct URL

        ## Available Tools
        ${toolNames.joinToString(", ")}
    """.trimIndent()

    private fun buildSearchPlaybook(): String = """
        ## Search Playbook (MEMORIZE)
        | Task | Correct approach |
        |------|------------------|
        | "search X on youtube" | browser_search(site="youtube", query="X") |
        | "open youtube and search X" | browser_search(site="youtube", query="X") |
        | "google X" | browser_search(site="google", query="X") |
        | Any search | browser_search → browser_wait(2000) → browser_snapshot → DONE if results show |

        WRONG: navigate to homepage → find search box → type → press Enter (fails on YouTube/React sites)
    """.trimIndent()

    private suspend fun buildSkillsTier(userPrompt: String): String {
        val store = skillStore ?: return ""
        val matched = TaskPreprocessor.matchedSkillNames(userPrompt)
        return buildString {
            if (matched.isNotEmpty()) {
                appendLine("## Active Skills (matched to this task — FOLLOW THESE)")
                for (name in matched) {
                    runCatching {
                        val body = store.readSkill(name)
                        appendLine("### Skill: $name")
                        appendLine(body.take(2500))
                        appendLine()
                    }
                }
            } else {
                val all = store.listSkills().take(8)
                if (all.isNotEmpty()) {
                    appendLine("## Available Skills (use skill_view to load)")
                    all.forEach { appendLine("- ${it.name}: ${it.description}") }
                }
            }
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
            appendLine("## Learned Strategies (proven heuristics — follow these)")
            strategies.forEach { appendLine("- [${it.domain}] ${it.heuristic}") }
        }
    }.trim()

    private fun buildVolatileTier(pageUrl: String?): String {
        if (pageUrl.isNullOrBlank()) return "## Current Page\nNo active page."
        return "## Current Page\nActive URL: $pageUrl"
    }
}