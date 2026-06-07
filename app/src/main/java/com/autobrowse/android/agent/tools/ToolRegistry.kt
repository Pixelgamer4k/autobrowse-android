package com.autobrowse.android.agent.tools

import com.autobrowse.android.browser.BrowserController
import com.autobrowse.android.domain.model.ToolDefinition

class ToolRegistry(
    tools: List<AgentTool>,
    private val browserController: BrowserController? = null,
) {
    private val toolMap = tools.associateBy { it.name }

    private companion object {
        val REFRESH_ONLY_BROWSER_TOOLS = setOf(
            "browser_wait",
            "browser_snapshot",
            "browser_tab_list",
            "browser_stop",
        )
    }

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
            when {
                name in REFRESH_ONLY_BROWSER_TOOLS ->
                    BrowserToolHelper.refreshPageContext(context, browserController)
                name in BrowserAdvancedTools.READ_ONLY_NAMES ->
                    BrowserToolHelper.refreshPageContext(context, browserController)
                else -> {
                    val waitMs = when (name) {
                        "browser_navigate", "browser_search", "browser_reload" -> 2500L
                        "browser_click", "browser_press", "browser_type", "browser_fill",
                        "browser_double_click", "browser_select_option", "browser_checkbox_toggle",
                        -> 1200L
                        "browser_forward", "browser_back" -> 1000L
                        "browser_dismiss_overlays", "browser_accept_cookies", "browser_close_modal" -> 1000L
                        else -> 800L
                    }
                    BrowserToolHelper.afterBrowserAction(context, browserController, waitMs)
                }
            }
        }
        return result
    }
}