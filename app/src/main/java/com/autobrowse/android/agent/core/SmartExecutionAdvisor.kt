package com.autobrowse.android.agent.core

import com.autobrowse.android.agent.tools.ToolExecutionContext
import com.autobrowse.android.domain.model.ToolCall
import com.autobrowse.android.domain.model.ToolResult
import com.autobrowse.android.feedback.FeedbackManager

object SmartExecutionAdvisor {
    var feedbackManager: FeedbackManager? = null
    private val CAPTCHA_HARMFUL_TOOLS = setOf(
        "browser_click",
        "browser_type",
        "browser_dismiss_overlays",
        "browser_click_xy",
        "browser_double_click",
        "browser_press_and_hold",
    )
    suspend fun augmentToolOutput(
        call: ToolCall,
        result: ToolResult,
        context: ToolExecutionContext,
        userPrompt: String,
        iteration: Int,
        priorToolNames: List<String>,
    ): String {
        val hints = mutableListOf<String>()
        val output = result.output

        if (call.name == "browser_search" && result.success) {
            val query = context.extractedData["search_query"].orEmpty()
            val pageText = context.pageText.orEmpty()
            if (query.isNotBlank() && pageText.contains(query, ignoreCase = true)) {
                hints += "✓ SEARCH VERIFIED: \"$query\" found in page. Summarize top results and STOP — do not search again."
            } else if (context.pageUrl?.contains("search", ignoreCase = true) == true ||
                context.pageUrl?.contains("q=", ignoreCase = true) == true
            ) {
                hints += "✓ Search URL loaded. browser_snapshot if needed, then summarize and STOP."
            }
        }

        if (call.name == "browser_snapshot" && result.success) {
            val query = TaskPreprocessor.extractSearchQuery(userPrompt)
            val pageText = context.pageText.orEmpty()
            if (query != null && pageText.contains(query, ignoreCase = true)) {
                hints += "✓ RESULTS VISIBLE for \"$query\". Task likely complete — respond to user now."
            }
            if (pageText.length < 80) {
                hints += "⚠ Snapshot nearly empty — browser_wait(2000) then snapshot again."
            }
        }

        if (context.captchaUserActionRequired) {
            hints += "🛑 CAPTCHA ACTIVE: call browser_solve_captcha on authorized sites (CapSolver/2Captcha). Fallback: browser_wait_for_captcha_clear."
            if (call.name in CAPTCHA_HARMFUL_TOOLS) {
                hints += "⚠ Avoid blind ${call.name} on CAPTCHA widgets — solve first."
            }
        } else if (context.captchaDetected) {
            hints += "⚠ Bot challenge detected — browser_detect_captcha then browser_solve_captcha if authorized."
        }

        if (iteration >= 6 && priorToolNames.count { it == "browser_type" } >= 2) {
            hints += "⚠ LOOP: browser_type repeated. Switch to browser_search or browser_click with @eN refs."
        }

        if (iteration >= 8 && priorToolNames.distinct().size <= 2) {
            hints += "⚠ STUCK: Try web_fetch, browser_vision, or skill_view for a different approach."
        }

        val feedbackHints = buildFeedbackHints(userPrompt)
        if (hints.isEmpty() && feedbackHints.isEmpty()) return output
        return buildString {
            appendLine(output)
            appendLine()
            appendLine("[Execution advisor]")
            feedbackHints.forEach { appendLine(it) }
            hints.forEach { appendLine(it) }
        }
    }

    private suspend fun buildFeedbackHints(userPrompt: String): List<String> {
        val manager = feedbackManager ?: return emptyList()
        val block = manager.buildPromptBlock(userPrompt, maxContextual = 4)
        if (block.mandatory.isEmpty()) return emptyList()
        val sources = block.mandatory.filter { it.category == "sources" }
        if (sources.isEmpty()) return emptyList()
        return listOf(
            "📌 MANDATORY SOURCES from training (apply now): " +
                sources.joinToString(" | ") { it.content.take(180) },
        )
    }

    fun shouldSerializeBrowserTools(toolCalls: List<ToolCall>): Boolean =
        toolCalls.count { it.name.startsWith("browser_") } > 1
}