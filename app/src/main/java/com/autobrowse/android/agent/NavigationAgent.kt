package com.autobrowse.android.agent

import com.autobrowse.android.agent.core.AgentLoop
import com.autobrowse.android.domain.model.AutoBrowseRequest
import com.autobrowse.android.domain.model.AutoBrowseResult
import com.autobrowse.android.domain.model.BrowserContext

/**
 * Thin facade over [AgentLoop] — Hermes-style multi-turn tool-calling agent.
 */
class NavigationAgent(
    private val agentLoop: AgentLoop,
) {
    suspend fun processUserMessage(
        request: AutoBrowseRequest,
        pageUrl: String?,
        pageHtml: String?,
        pageText: String?,
    ): AutoBrowseResult {
        val result = agentLoop.runConversation(
            request = request,
            browserContext = BrowserContext(pageUrl, pageHtml, pageText),
        )
        return AutoBrowseResult(
            success = result.success,
            summary = result.finalResponse,
            extractedData = result.extractedData,
            actions = result.actions,
            error = result.error,
        )
    }
}