package com.autobrowse.android.agent.tools

import com.autobrowse.android.browser.WindowManager
import com.autobrowse.android.domain.model.AgentAction

class BrowserWindowArrangeTool(
    private val windowManager: WindowManager,
) : AgentTool {
    override val name = "browser_window_arrange"
    override val description =
        "Arrange open browser windows for visibility. Focus tab becomes larger and in front; " +
            "background tabs shrink and spread out. Use after opening multiple tabs for comparison or research."
    override val parametersJson = """
        {"type":"object","properties":{
          "focus_tab_id":{"type":"string","description":"Primary tab to enlarge and bring forward"},
          "tab_ids":{"type":"array","items":{"type":"string"},"description":"Optional subset of tab ids to arrange"}
        },"required":["focus_tab_id"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val manager = context.windowManager ?: windowManager
        val focusTabId = args["focus_tab_id"]?.toString().orEmpty()
        if (focusTabId.isBlank()) {
            return ToolExecutionResult("focus_tab_id is required", success = false)
        }
        @Suppress("UNCHECKED_CAST")
        val tabIds = (args["tab_ids"] as? List<*>)?.mapNotNull { it?.toString() }
        val windows = manager.arrangeWindows(focusTabId, tabIds)
        context.extractedData["active_tab_id"] = focusTabId
        context.browserActions += AgentAction(
            type = "window_arrange",
            target = focusTabId,
            value = windows.size.toString(),
            reasoning = "Arranged ${windows.size} windows with focus on $focusTabId",
        )
        val summary = windows.joinToString("\n") { window ->
            val active = if (window.isActive) " [FOCUS]" else ""
            "- ${window.tabId}$active: ${(window.layout.widthFraction * 100).toInt()}% wide " +
                "@ (${"%.2f".format(window.layout.offsetX)}, ${"%.2f".format(window.layout.offsetY)})"
        }
        return ToolExecutionResult("Arranged ${windows.size} windows.\n$summary")
    }
}

class BrowserWindowResizeTool(
    private val windowManager: WindowManager,
) : AgentTool {
    override val name = "browser_window_resize"
    override val description =
        "Resize and optionally reposition a browser window using fraction coordinates (0.0–1.0)."
    override val parametersJson = """
        {"type":"object","properties":{
          "tab_id":{"type":"string"},
          "width_fraction":{"type":"number","description":"Window width as fraction of canvas (0.30–1.0)"},
          "offset_x":{"type":"number","description":"Optional left offset fraction"},
          "offset_y":{"type":"number","description":"Optional top offset fraction"}
        },"required":["tab_id","width_fraction"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val manager = context.windowManager ?: windowManager
        val tabId = args["tab_id"]?.toString().orEmpty()
        val width = (args["width_fraction"] as? Number)?.toFloat()
            ?: return ToolExecutionResult("width_fraction is required", success = false)
        val offsetX = (args["offset_x"] as? Number)?.toFloat()
        val offsetY = (args["offset_y"] as? Number)?.toFloat()
        val window = manager.resizeWindow(tabId, width, offsetX, offsetY)
        return ToolExecutionResult(
            "Resized ${window.tabId} to ${(window.layout.widthFraction * 100).toInt()}% " +
                "@ (${"%.2f".format(window.layout.offsetX)}, ${"%.2f".format(window.layout.offsetY)})",
        )
    }
}

class BrowserWindowFocusTool(
    private val windowManager: WindowManager,
) : AgentTool {
    override val name = "browser_window_focus"
    override val description = "Bring a browser window to the front and mark it active."
    override val parametersJson = """
        {"type":"object","properties":{"tab_id":{"type":"string"}},"required":["tab_id"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val manager = context.windowManager ?: windowManager
        val tabId = args["tab_id"]?.toString().orEmpty()
        val window = manager.focusWindow(tabId)
        context.extractedData["active_tab_id"] = tabId
        return ToolExecutionResult("Focused window ${window.tabId} — ${window.title}")
    }
}

class BrowserWindowListTool(
    private val windowManager: WindowManager,
) : AgentTool {
    override val name = "browser_window_list"
    override val description = "List open browser windows with layout, z-order, and focus state."
    override val parametersJson = """{"type":"object","properties":{}}"""

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val manager = context.windowManager ?: windowManager
        val lines = manager.listWindows().joinToString("\n") { window ->
            val active = if (window.isActive) " [FOCUS]" else ""
            "- ${window.tabId}$active z=${window.zIndex}: ${window.title} — " +
                "${(window.layout.widthFraction * 100).toInt()}% @ " +
                "(${"%.2f".format(window.layout.offsetX)}, ${"%.2f".format(window.layout.offsetY)})"
        }
        return ToolExecutionResult(lines.ifBlank { "No windows open." })
    }
}