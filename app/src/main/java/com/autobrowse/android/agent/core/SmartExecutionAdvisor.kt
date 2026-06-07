package com.autobrowse.android.agent.core

import com.autobrowse.android.agent.tools.ToolExecutionContext
import com.autobrowse.android.domain.model.ToolCall
import com.autobrowse.android.domain.model.ToolResult

object SmartExecutionAdvisor {
    fun augmentToolOutput(
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

        if (iteration >= 6 && priorToolNames.count { it == "browser_type" } >= 2) {
            hints += "⚠ LOOP: browser_type repeated. Switch to browser_search or browser_click with @eN refs."
        }

        if (iteration >= 8 && priorToolNames.distinct().size <= 2) {
            hints += "⚠ STUCK: Try web_fetch, browser_vision, or skill_view for a different approach."
        }

        if (hints.isEmpty()) return output
        return buildString {
            appendLine(output)
            appendLine()
            appendLine("[Execution advisor]")
            hints.forEach { appendLine(it) }
        }
    }

    fun shouldSerializeBrowserTools(toolCalls: List<ToolCall>): Boolean =
        toolCalls.count { it.name.startsWith("browser_") } > 1
}