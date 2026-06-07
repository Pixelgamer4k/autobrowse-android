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

class BrowserSnapshotTool(
    private val browserController: BrowserController,
) : AgentTool {
    override val name = "browser_snapshot"
    override val description = "Get visible page text and current URL from the active browser tab."
    override val parametersJson = """{"type":"object","properties":{}}"""

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val url = browserController.getCurrentUrl() ?: context.pageUrl.orEmpty()
        val text = browserController.getPageText() ?: context.pageText.orEmpty()
        val preview = text.take(6000)
        context.extractedData["page_url"] = url
        context.extractedData["page_text_preview"] = preview
        return ToolExecutionResult("URL: $url\n\nVisible text:\n$preview")
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
    override val description = "Click an element on the current page using a CSS selector."
    override val parametersJson = """
        {"type":"object","properties":{"selector":{"type":"string"}},"required":["selector"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val selector = args["selector"]?.toString().orEmpty()
        if (selector.isBlank()) return ToolExecutionResult("selector is required", success = false)
        val script = """
            (function() {
                var el = document.querySelector('$selector');
                if (!el) return 'not_found';
                el.click();
                return 'clicked';
            })();
        """.trimIndent()
        val result = browserController.evaluateScript(script)
        context.browserActions += AgentAction(type = "click", target = selector, reasoning = "Agent clicked element")
        return ToolExecutionResult("click $selector: ${result ?: "unknown"}")
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