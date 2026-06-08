package com.autobrowse.android.agent.core

import com.autobrowse.android.agent.memory.MemoryManager
import com.autobrowse.android.agent.training.TrainingCorpusLoader
import com.autobrowse.android.agent.trajectory.TrajectoryStore
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
    private val trainingCorpus: TrainingCorpusLoader? = null,
    private val trajectoryStore: TrajectoryStore? = null,
) {
    /**
     * Minimal on-device prompt — skips training corpus, skill bodies, and long playbooks
     * so the first token is not blocked by multi-thousand-token prefill.
     */
    fun buildForLocal(
        userPrompt: String,
        pageUrl: String?,
    ): String = buildString {
        appendLine("You are Multiwindow Autobrowser, a browser automation agent on Android.")
        appendLine("Rules: browser_search for searches; browser_snapshot before click; finish in ≤5 tools for simple tasks.")
        appendLine("Call tools immediately. No long internal reasoning.")
        if (!pageUrl.isNullOrBlank()) {
            appendLine("Current page: $pageUrl")
        }
        val hints = TaskPreprocessor.hintsForPrompt(userPrompt).take(2)
        hints.forEach { appendLine("Hint: $it") }
    }.trim()

    suspend fun build(
        prefetchedMemory: String,
        strategies: List<LearnedStrategy>,
        pageUrl: String?,
        enabledToolNames: List<String>,
        userPrompt: String = "",
    ): String {
        val stable = buildStableTier(enabledToolNames)
        val searchPlaybook = buildSearchPlaybook()
        val hints = buildInternalHintsTier(userPrompt)
        val training = buildTrainingTier(userPrompt)
        val context = buildContextTier(prefetchedMemory, strategies, userPrompt)
        val skills = buildSkillsTier(userPrompt)
        val volatile = buildVolatileTier(pageUrl)
        return listOf(stable, searchPlaybook, hints, training, context, skills, volatile)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    private fun buildStableTier(toolNames: List<String>): String = """
        # Multiwindow Autobrowser Agent
        You are Multiwindow Autobrowser — an expert browser automation agent on Android. Complete tasks in MINIMAL steps.

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
        - Multi-window: browser_tab_open (auto-arranges) → browser_window_arrange to refocus/resize
        - Extract: browser_get_links, browser_readability, browser_extract_*
        - Wait: browser_wait_for_text, browser_wait_for_url, browser_wait_for_element
        - Popups: browser_accept_cookies, browser_dismiss_overlays, browser_close_modal
        - Stuck: browser_vision (cloud) or browser_screenshot + browser_execute_js

        ## Multi-Window Layout
        - Opening 2+ tabs auto-arranges windows (focus tab larger, others smaller)
        - Use browser_window_arrange(focus_tab_id) after parallel research or product comparison
        - Use browser_window_focus to bring the important result forward
        - Use browser_window_list to inspect positions before rearranging

        ## Available Tools (${toolNames.size} total)
        ${toolNames.sorted().joinToString(", ")}
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

    private fun buildInternalHintsTier(userPrompt: String): String {
        val hints = TaskPreprocessor.hintsForPrompt(userPrompt)
        if (hints.isEmpty()) return ""
        return buildString {
            appendLine("## Task Hints (internal — do not repeat to user)")
            hints.forEach { appendLine("- $it") }
        }.trim()
    }

    private suspend fun buildSkillsTier(userPrompt: String): String {
        val store = skillStore ?: return ""
        val allSkills = store.listSkills()
        val matched = TaskPreprocessor.matchedSkillNames(userPrompt, allSkills)
        return buildString {
            if (matched.isNotEmpty()) {
                appendLine("## Active Skills (matched to this task — FOLLOW THESE)")
                for (name in matched) {
                    runCatching {
                        val body = store.readSkill(name)
                        val meta = allSkills.find { it.name == name }
                        val tag = meta?.category?.let { "[$it]" }.orEmpty()
                        appendLine("### Skill: $name $tag")
                        appendLine(body.take(2800))
                        appendLine()
                    }
                }
            } else {
                val learned = allSkills.filter { it.category == "learned" }.take(4)
                val bundled = allSkills.filter { it.category == "bundled" }.take(6)
                if (learned.isNotEmpty() || bundled.isNotEmpty()) {
                    appendLine("## Available Skills (use skill_view to load)")
                    learned.forEach { appendLine("- [learned] ${it.name}: ${it.description}") }
                    bundled.forEach { appendLine("- ${it.name}: ${it.description}") }
                }
            }
        }.trim()
    }

    private suspend fun buildTrainingTier(userPrompt: String): String {
        val loader = trainingCorpus ?: return ""
        val block = loader.buildTrainingContext(userPrompt)
        if (block.isBlank()) return ""
        return "## Training Corpus (gold trajectories + anti-patterns)\n$block"
    }

    private suspend fun buildContextTier(
        prefetchedMemory: String,
        strategies: List<LearnedStrategy>,
        userPrompt: String,
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
        val failures = trajectoryStore?.getRecentFailures(3).orEmpty()
        if (failures.isNotEmpty()) {
            appendLine()
            appendLine("## Recent failures (do NOT repeat)")
            failures.forEach { f ->
                appendLine("- \"${f.prompt.take(60)}\" → ${f.reflection ?: "failed trajectory"}")
            }
        }
    }.trim()

    private fun buildVolatileTier(pageUrl: String?): String {
        if (pageUrl.isNullOrBlank()) return "## Current Page\nNo active page."
        return "## Current Page\nActive URL: $pageUrl"
    }
}