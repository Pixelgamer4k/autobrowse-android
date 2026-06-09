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
        private const val MAX_CONTEXTUAL_FEEDBACK = 6
        private const val MAX_STRATEGY_LINES = 2
    }

    suspend fun assemble(
        userPrompt: String,
        strategies: List<LearnedStrategy>,
        pageUrl: String?,
        toolCount: Int,
    ): PromptBundle {
        val feedbackBlock = feedbackManager?.buildPromptBlock(userPrompt, MAX_CONTEXTUAL_FEEDBACK)
        val stable = buildStablePrefix(toolCount, feedbackBlock?.formatMandatory().orEmpty())
        val volatile = buildVolatileContext(userPrompt, strategies, pageUrl, feedbackBlock?.formatContextual().orEmpty())
        return PromptBundle(stablePrefix = stable, volatileContext = volatile)
    }

    suspend fun assembleLocal(userPrompt: String, pageUrl: String?): PromptBundle {
        val feedbackBlock = feedbackManager?.buildPromptBlock(userPrompt, maxContextual = 4)
        val stable = buildString {
            append(buildLocalStablePrefix())
            val mandatory = feedbackBlock?.formatMandatory(maxCharsPerEntry = 300).orEmpty()
            if (mandatory.isNotBlank()) {
                appendLine()
                append(mandatory)
            }
        }.trim()
        val volatile = buildString {
            val memory = memoryManager.getBoundedCuratedBlock(maxChars = 500)
            if (memory.isNotBlank()) {
                appendLine(memory)
                appendLine()
            }
            val contextual = feedbackBlock?.formatContextual(maxCharsPerEntry = 200).orEmpty()
            if (contextual.isNotBlank()) {
                appendLine(contextual)
                appendLine()
            }
            if (FeedbackDetector.isLikelyFeedback(userPrompt)) {
                appendLine("Current message is coaching — apply immediately and call feedback_submit.")
                appendLine()
            }
            if (!pageUrl.isNullOrBlank()) appendLine("Page: $pageUrl")
            TaskPreprocessor.hintsForPrompt(userPrompt).take(3).forEach { appendLine("Hint: $it") }
        }.trim()
        return PromptBundle(stablePrefix = stable, volatileContext = volatile)
    }

    suspend fun rebuildVolatile(
        userPrompt: String,
        strategies: List<LearnedStrategy>,
        pageUrl: String?,
    ): String {
        val feedbackBlock = feedbackManager?.buildPromptBlock(userPrompt, MAX_CONTEXTUAL_FEEDBACK)
        return buildVolatileContext(
            userPrompt = userPrompt,
            strategies = strategies,
            pageUrl = pageUrl,
            contextualFeedback = feedbackBlock?.formatContextual().orEmpty(),
        )
    }

    private suspend fun buildStablePrefix(toolCount: Int, mandatoryFeedback: String): String = buildString {
        appendLine("# Multiwindow Autobrowser")
        appendLine("Browser automation agent on Android. MINIMAL steps. Tools via API.")
        appendLine()
        appendLine("## Rules")
        appendLine("1. SEARCH → browser_search (never type in site search boxes)")
        appendLine("2. browser_wait → browser_snapshot before click")
        appendLine("3. Simple search = 3-5 tools then STOP")
        appendLine("4. Use @eN refs from snapshot for clicks")
        appendLine("5. Skills: metadata below only — call skill_view(name) for full playbook")
        appendLine("6. Feedback coaching → ALWAYS apply mandatory training + feedback_submit")
        appendLine("7. CAPTCHA on authorized sites → browser_solve_captcha (CapSolver/2Captcha); else browser_detect_captcha")
        appendLine("8. Mandatory training below overrides defaults — follow even in new sessions")
        appendLine()
        if (mandatoryFeedback.isNotBlank()) {
            appendLine(mandatoryFeedback)
            appendLine()
        }
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
        skill_view(name) loads full playbooks. Mandatory training feedback ALWAYS applies across sessions.
        feedback_submit saves coaching. browser_solve_captcha on authorized sites when CAPTCHA appears.
    """.trimIndent()

    private suspend fun buildVolatileContext(
        userPrompt: String,
        strategies: List<LearnedStrategy>,
        pageUrl: String?,
        contextualFeedback: String,
    ): String = buildString {
        val hints = TaskPreprocessor.hintsForPrompt(userPrompt).take(4)
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
        if (contextualFeedback.isNotBlank()) {
            appendLine(contextualFeedback)
            appendLine()
        }
        if (FeedbackDetector.isLikelyFeedback(userPrompt)) {
            appendLine("## Coaching now")
            appendLine("- User is training you — apply mandatory feedback + this message immediately.")
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
}