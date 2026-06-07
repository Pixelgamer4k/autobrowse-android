package com.autobrowse.android.agent.tools

import com.autobrowse.android.browser.BrowserController
import com.autobrowse.android.browser.TabManager
import com.autobrowse.android.domain.model.AgentAction

class BrowserTabOpenTool(
    private val tabManager: TabManager,
    private val browserController: BrowserController,
) : AgentTool {
    override val name = "browser_tab_open"
    override val description = "Open a new browser tab, optionally navigating to a URL."
    override val parametersJson = """
        {"type":"object","properties":{"url":{"type":"string","description":"Optional URL to load"}}}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val manager = context.tabManager ?: tabManager
        val tab = manager.openTab(args["url"]?.toString())
        args["url"]?.toString()?.let { browserController.loadUrl(it, tab.id) }
        context.extractedData["active_tab_id"] = tab.id
        context.browserActions += AgentAction(type = "tab_open", target = tab.id, value = tab.url, reasoning = "Opened tab")
        return ToolExecutionResult("Opened tab ${tab.id} — ${tab.title} (${tab.url})")
    }
}

class BrowserTabCloseTool(
    private val tabManager: TabManager,
) : AgentTool {
    override val name = "browser_tab_close"
    override val description = "Close a browser tab. Closes active tab if tab_id omitted."
    override val parametersJson = """
        {"type":"object","properties":{"tab_id":{"type":"string"}}}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val manager = context.tabManager ?: tabManager
        val closed = manager.closeTab(args["tab_id"]?.toString())
            ?: return ToolExecutionResult("No tab to close", success = false)
        return ToolExecutionResult("Closed tab ${closed.id} — ${closed.title}")
    }
}

class BrowserTabSwitchTool(
    private val tabManager: TabManager,
) : AgentTool {
    override val name = "browser_tab_switch"
    override val description = "Switch the active browser tab."
    override val parametersJson = """
        {"type":"object","properties":{"tab_id":{"type":"string"}},"required":["tab_id"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val tabId = args["tab_id"]?.toString().orEmpty()
        val manager = context.tabManager ?: tabManager
        val tab = manager.switchTab(tabId)
        context.extractedData["active_tab_id"] = tab.id
        return ToolExecutionResult("Switched to tab ${tab.id} — ${tab.title} (${tab.url})")
    }
}

class BrowserTabListTool(
    private val tabManager: TabManager,
) : AgentTool {
    override val name = "browser_tab_list"
    override val description = "List all open browser tabs with ids, titles, and URLs."
    override val parametersJson = """{"type":"object","properties":{}}"""

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val manager = context.tabManager ?: tabManager
        val lines = manager.listTabs().joinToString("\n") { tab ->
            val active = if (tab.isActive) " [ACTIVE]" else ""
            "- ${tab.id}$active: ${tab.title} — ${tab.url}"
        }
        return ToolExecutionResult(lines.ifBlank { "No tabs open." })
    }
}