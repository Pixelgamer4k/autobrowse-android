package com.autobrowse.android.agent.tools

import com.autobrowse.android.browser.BrowserController
import com.autobrowse.android.domain.model.ToolDefinition

class ToolRegistry(
    tools: List<AgentTool>,
    private val browserController: BrowserController? = null,
) {
    private val toolMap = tools.associateBy { it.name }

    fun definitions(): List<ToolDefinition> = toolMap.values.map { it.toDefinition() }

    fun get(name: String): AgentTool? = toolMap[name]

    suspend fun dispatch(
        name: String,
        args: Map<String, Any?>,
        context: ToolExecutionContext,
    ): ToolExecutionResult {
        val tool = toolMap[name]
            ?: return ToolExecutionResult(output = "Unknown tool: $name", success = false)
        val result = runCatching { tool.execute(args, context) }
            .getOrElse { ToolExecutionResult(output = "Tool error: ${it.message}", success = false) }
        if (browserController != null && BrowserToolHelper.isBrowserTool(name)) {
            when (name) {
                "browser_wait", "browser_snapshot", "browser_tab_list" ->
                    BrowserToolHelper.refreshPageContext(context, browserController)
                else -> {
                    val waitMs = when (name) {
                        "browser_navigate", "browser_search" -> 2500L
                        "browser_click", "browser_press", "browser_type", "browser_fill" -> 1200L
                        else -> 800L
                    }
                    BrowserToolHelper.afterBrowserAction(context, browserController, waitMs)
                }
            }
        }
        return result
    }
}