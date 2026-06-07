package com.autobrowse.android.agent.tools

import com.autobrowse.android.browser.BrowserController
import com.autobrowse.android.domain.model.AgentAction
import org.json.JSONObject

class BrowserNavigateTool(
    private val browserController: BrowserController,
) : AgentTool {
    override val name = "browser_navigate"
    override val description = "Navigate the active browser tab to a URL."
    override val parametersJson = """
        {"type":"object","properties":{"url":{"type":"string","description":"Full URL to open"}},"required":["url"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val url = args["url"]?.toString()?.trim().orEmpty()
        if (url.isBlank()) return ToolExecutionResult("url is required", success = false)
        browserController.loadUrl(url)
        context.browserActions += AgentAction(type = "navigate", target = url, reasoning = "Agent navigated browser")
        return ToolExecutionResult("Navigated to $url")
    }
}

class BrowserFillTool(
    private val browserController: BrowserController,
) : AgentTool {
    override val name = "browser_fill"
    override val description = "Fill a form field on the current page using a CSS selector."
    override val parametersJson = """
        {"type":"object","properties":{"selector":{"type":"string"},"value":{"type":"string"}},"required":["selector","value"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val selector = args["selector"]?.toString().orEmpty()
        val value = args["value"]?.toString().orEmpty()
        if (selector.isBlank()) return ToolExecutionResult("selector is required", success = false)
        val action = AgentAction(type = "fill", target = selector, value = value, reasoning = "Agent filled form field")
        context.browserActions += action
        val results = browserController.executeActions(listOf(action))
        return ToolExecutionResult(results.joinToString("\n"))
    }
}

class BrowserClickTool(
    private val browserController: BrowserController,
) : AgentTool {
    override val name = "browser_click"
    override val description = "Click an element by ref (@eN from browser_snapshot) or CSS selector."
    override val parametersJson = """
        {"type":"object","properties":{"ref":{"type":"string"},"selector":{"type":"string"},"tab_id":{"type":"string"}}}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val tabId = args["tab_id"]?.toString() ?: context.activeTabId
        val ref = args["ref"]?.toString()
        val selector = args["selector"]?.toString()
        val result = when {
            !ref.isNullOrBlank() -> browserController.clickRef(ref, tabId)
            !selector.isNullOrBlank() -> browserController.evaluateScript("""
                (function() {
                    var el = document.querySelector('$selector');
                    if (!el) return 'not_found';
                    el.click();
                    return 'clicked';
                })();
            """.trimIndent(), tabId)
            else -> return ToolExecutionResult("ref or selector required", success = false)
        }
        val target = ref ?: selector.orEmpty()
        context.browserActions += AgentAction(type = "click", target = target, reasoning = "Agent clicked element")
        return ToolExecutionResult("click $target: ${result ?: "unknown"}")
    }
}

fun parseToolArgs(argumentsJson: String): Map<String, Any?> {
    return runCatching {
        val json = JSONObject(argumentsJson.ifBlank { "{}" })
        json.keys().asSequence().associateWith { key ->
            json.get(key).let { value ->
                when (value) {
                    JSONObject.NULL -> null
                    else -> value
                }
            }
        }
    }.getOrDefault(emptyMap())
}