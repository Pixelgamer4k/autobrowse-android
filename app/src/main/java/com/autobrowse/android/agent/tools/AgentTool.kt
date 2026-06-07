package com.autobrowse.android.agent.tools

import com.autobrowse.android.browser.TabManager
import com.autobrowse.android.domain.model.ToolDefinition

data class ToolExecutionContext(
    val sessionId: String,
    var pageUrl: String?,
    var pageHtml: String?,
    var pageText: String?,
    val activeTabId: String? = null,
    val tabManager: TabManager? = null,
    val browserActions: MutableList<com.autobrowse.android.domain.model.AgentAction> = mutableListOf(),
    val extractedData: MutableMap<String, String> = mutableMapOf(),
    val pendingVisionImages: MutableList<String> = mutableListOf(),
    val parallelTaskResults: MutableMap<String, String> = mutableMapOf(),
)

data class ToolExecutionResult(
    val output: String,
    val success: Boolean = true,
)

interface AgentTool {
    val name: String
    val description: String
    val parametersJson: String
    suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult
    fun toDefinition(): ToolDefinition = ToolDefinition(name, description, parametersJson)
}