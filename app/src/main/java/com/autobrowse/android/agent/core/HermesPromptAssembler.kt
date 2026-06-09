package com.autobrowse.android.agent.core

import com.autobrowse.android.agent.memory.MemoryManager
import com.autobrowse.android.domain.model.LearnedStrategy
import com.autobrowse.android.feedback.FeedbackDetector
import com.autobrowse.android.feedback.FeedbackManager
import com.autobrowse.android.skills.SkillStore

/**
 * Ultra-fast Hermes-style prompt: stable cached prefix + bounded memory + skill metadata only.
 */
class HermesPromptAssembler(
    private val memoryManager: MemoryManager,
    private val skillStore: SkillStore? = null,
    private val feedbackManager: FeedbackManager? = null,
) {
    companion object {
        private const val BOUNDED_MEMORY_CHARS = 900
        private const val MAX_SKILL_LINES = 14
        private const val MAX_FEEDBACK_LINES = 2
        private const val MAX_STRATEGY_LINES = 2
    }

    suspend fun assemble(
        userPrompt: String,
        strategies: List<LearnedStrategy>,
        pageUrl: String?,
        toolCount: Int,
    ): PromptBundle {
        val stable = buildStablePrefix(toolCount)
        val volatile = buildVolatileContext(userPrompt, strategies, pageUrl)
        return PromptBundle(stablePrefix = stable, volatileContext = volatile)
    }

    suspend fun assembleLocal(userPrompt: String, pageUrl: String?): PromptBundle {
        val stable = buildLocalStablePrefix()
        val volatile = buildString {
            val memory = memoryManager.getBoundedCuratedBlock(maxChars = 500)
            if (memory.isNotBlank()) {
                appendLine(memory)
                appendLine()
            }
            val feedback = buildFeedbackVolatile(userPrompt, compact = true)
            if (feedback.isNotBlank()) {
                appendLine(feedback)
                appendLine()
            }
            if (!pageUrl.isNullOrBlank()) appendLine("Page: $pageUrl")
            TaskPreprocessor.hintsForPrompt(userPrompt).take(2).forEach { appendLine("Hint: $it") }
        }.trim()
        return PromptBundle(stablePrefix = stable, volatileContext = volatile)
    }

    private suspend fun buildStablePrefix(toolCount: Int): String = buildString {
        appendLine("# Multiwindow Autobrowser")
        appendLine("Browser automation agent on Android. MINIMAL steps. Tools via API.")
        appendLine()
        appendLine("## Rules")
        appendLine("1. SEARCH → browser_search (never type in site search boxes)")
        appendLine("2. browser_wait → browser_snapshot before click")
        appendLine("3. Simple search = 3-5 tools then STOP")
        appendLine("4. Use @eN refs from snapshot for clicks")
        appendLine("5. Skills: metadata below only — call skill_view(name) for full playbook")
        appendLine("6. Feedback coaching → apply + feedback_submit")
        appendLine("7. CAPTCHA → stop automating; user solves in browser; browser_wait_for_captcha_clear")
        appendLine()
        appendLine("## Skills (progressive disclosure)")
        append(skillMetadataBlock())
        appendLine()
        appendLine(memoryManager.getBoundedCuratedBlock(BOUNDED_MEMORY_CHARS))
        appendLine()
        appendLine("## Tools")
        appendLine("$toolCount tools attached. Core: browser_search, browser_snapshot, browser_click, browser_tab_open, skill_view.")
    }.trim()

    private fun buildLocalStablePrefix(): String = """
        # Multiwindow Autobrowser (local)
        browser_search for searches; browser_snapshot before click; ≤5 tools for simple tasks.
        skill_view(name) loads full playbooks. feedback_submit saves coaching.
    """.trimIndent()

    private suspend fun buildVolatileContext(
        userPrompt: String,
        strategies: List<LearnedStrategy>,
        pageUrl: String?,
    ): String = buildString {
        val hints = TaskPreprocessor.hintsForPrompt(userPrompt).take(3)
        if (hints.isNotEmpty()) {
            appendLine("## Task hints")
            hints.forEach { appendLine("- $it") }
            appendLine()
        }
        val matched = TaskPreprocessor.matchedSkillNames(userPrompt, skillStore?.listSkills().orEmpty())
        if (matched.isNotEmpty()) {
            appendLine("## Matched skills (skill_view to load)")
            matched.take(4).forEach { appendLine("- $it") }
            appendLine()
        }
        val feedback = buildFeedbackVolatile(userPrompt)
        if (feedback.isNotBlank()) {
            appendLine(feedback)
            appendLine()
        }
        strategies.take(MAX_STRATEGY_LINES).forEach {
            appendLine("- Strategy [${it.domain}]: ${it.heuristic.take(100)}")
        }
        if (strategies.isNotEmpty()) appendLine()
        if (!pageUrl.isNullOrBlank()) appendLine("Page: $pageUrl")
    }.trim()

    private suspend fun skillMetadataBlock(): String {
        val store = skillStore ?: return "- (none loaded)"
        val all = store.listSkills()
        if (all.isEmpty()) return "- (run a task to seed skills)"
        val learned = all.filter { it.category == "learned" }.take(6)
        val bundled = all.filter { it.category != "learned" }.take(MAX_SKILL_LINES - learned.size)
        return buildString {
            (learned + bundled).take(MAX_SKILL_LINES).forEach { meta ->
                appendLine("- ${meta.name}: ${meta.description.take(72)}")
            }
        }.trim()
    }

    private suspend fun buildFeedbackVolatile(userPrompt: String, compact: Boolean = false): String {
        val manager = feedbackManager ?: return ""
        val isCoaching = FeedbackDetector.isLikelyFeedback(userPrompt)
        val limit = if (compact) 2 else MAX_FEEDBACK_LINES
        val relevant = if (userPrompt.isNotBlank()) {
            manager.searchRelevant(userPrompt, limit = limit)
        } else {
            emptyList()
        }
        val top = manager.getForPrompt(limit = limit)
        val lines = (relevant + top).distinctBy { it.id }.take(limit)
        if (lines.isEmpty() && !isCoaching) return ""
        return buildString {
            appendLine("## Training feedback")
            lines.forEach { e ->
                appendLine("- [${e.category}|p${e.priorityScore}] ${e.content.take(if (compact) 160 else 220)}")
            }
            if (isCoaching) appendLine("- Current message is coaching — apply now.")
        }.trim()
    }
}