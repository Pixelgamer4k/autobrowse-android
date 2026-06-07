package com.autobrowse.android.agent.tools

import com.autobrowse.android.browser.BrowserController
import com.autobrowse.android.domain.model.AgentAction
import org.json.JSONArray

class BrowserInteractiveSnapshotTool(
    private val browserController: BrowserController,
) : AgentTool {
    override val name = "browser_snapshot"
    override val description = "Get page URL, visible text, and interactive element refs (@e0, @e1, …) for reliable clicking."
    override val parametersJson = """
        {"type":"object","properties":{"interactive":{"type":"boolean","description":"Include interactive refs (default true)"},"tab_id":{"type":"string"}}}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val interactive = args["interactive"]?.toString()?.toBooleanStrictOrNull() ?: true
        val tabId = args["tab_id"]?.toString() ?: context.activeTabId
        val url = browserController.getCurrentUrl(tabId) ?: context.pageUrl.orEmpty()
        if (interactive) {
            val snapshot = browserController.getInteractiveSnapshot(tabId)
            if (snapshot != null) {
                val refs = snapshot.optJSONArray("refs") ?: JSONArray()
                val text = snapshot.optString("text", "")
                context.extractedData["page_url"] = url
                context.extractedData["page_text_preview"] = text.take(6000)
                val refLines = buildString {
                    for (i in 0 until refs.length()) {
                        val ref = refs.getJSONObject(i)
                        val label = ref.optString("label")
                        val role = ref.optString("role")
                        appendLine(
                            "${ref.optString("ref")} <${ref.optString("tag")}${if (role.isNotBlank()) "/$role" else ""}> " +
                                "${ref.optString("text").ifBlank { label }}",
                        )
                    }
                }
                val ready = snapshot.optString("readyState", "")
                return ToolExecutionResult(
                    "URL: $url\nTitle: ${snapshot.optString("title")}\nReady: $ready\n\n" +
                        "Interactive refs:\n$refLines\n\nVisible text:\n${text.take(5000)}",
                )
            }
        }
        val text = browserController.getPageText(tabId) ?: context.pageText.orEmpty()
        context.extractedData["page_url"] = url
        context.extractedData["page_text_preview"] = text.take(6000)
        return ToolExecutionResult("URL: $url\n\nVisible text:\n${text.take(6000)}")
    }
}

class BrowserTypeTool(private val browserController: BrowserController) : AgentTool {
    override val name = "browser_type"
    override val description = "Type text into an element by ref (@eN) or CSS selector. Clears field first."
    override val parametersJson = """
        {"type":"object","properties":{"ref":{"type":"string"},"selector":{"type":"string"},"text":{"type":"string"},"tab_id":{"type":"string"}},"required":["text"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val text = args["text"]?.toString().orEmpty()
        val tabId = args["tab_id"]?.toString() ?: context.activeTabId
        val ref = args["ref"]?.toString()
        val selector = args["selector"]?.toString()
        val result = when {
            !ref.isNullOrBlank() -> browserController.typeRef(ref, text, tabId)
            !selector.isNullOrBlank() -> {
                val escaped = text.replace("\\", "\\\\").replace("'", "\\'")
                browserController.evaluateScript("""
                    (function() {
                        var el = document.querySelector('$selector');
                        if (!el) return 'not_found';
                        el.focus(); el.value = '$escaped';
                        el.dispatchEvent(new Event('input', {bubbles:true}));
                        return 'typed';
                    })();
                """.trimIndent(), tabId)
            }
            else -> return ToolExecutionResult("ref or selector required", success = false)
        }
        val target = ref ?: selector.orEmpty()
        context.browserActions += AgentAction(type = "fill", target = target, value = text, reasoning = "Agent typed text")
        return ToolExecutionResult("type $target: ${result ?: "unknown"}")
    }
}

class BrowserScrollTool(private val browserController: BrowserController) : AgentTool {
    override val name = "browser_scroll"
    override val description = "Scroll the page up or down."
    override val parametersJson = """
        {"type":"object","properties":{"direction":{"type":"string","enum":["up","down"]},"amount":{"type":"integer"},"tab_id":{"type":"string"}},"required":["direction"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val direction = args["direction"]?.toString() ?: "down"
        val amount = args["amount"]?.toString()?.toIntOrNull() ?: 500
        val tabId = args["tab_id"]?.toString() ?: context.activeTabId
        val result = browserController.scroll(direction, amount, tabId)
        context.browserActions += AgentAction(type = "scroll", value = direction, reasoning = "Agent scrolled $direction")
        return ToolExecutionResult(result ?: "scrolled")
    }
}

class BrowserPressTool(private val browserController: BrowserController) : AgentTool {
    override val name = "browser_press"
    override val description = "Press a keyboard key (Enter, Tab, Escape, ArrowDown, etc.) on the active element."
    override val parametersJson = """
        {"type":"object","properties":{"key":{"type":"string"},"tab_id":{"type":"string"}},"required":["key"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val key = args["key"]?.toString().orEmpty()
        val tabId = args["tab_id"]?.toString() ?: context.activeTabId
        val result = browserController.pressKey(key, tabId)
        return ToolExecutionResult(result ?: "pressed")
    }
}

class BrowserBackTool(private val browserController: BrowserController) : AgentTool {
    override val name = "browser_back"
    override val description = "Navigate back in browser history."
    override val parametersJson = """{"type":"object","properties":{"tab_id":{"type":"string"}}}"""

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val tabId = args["tab_id"]?.toString() ?: context.activeTabId
        browserController.goBack(tabId)
        return ToolExecutionResult("Navigated back")
    }
}

class BrowserClickXYTool(private val browserController: BrowserController) : AgentTool {
    override val name = "browser_click_xy"
    override val description = "Click at viewport coordinates (mouse-level interaction for canvas/custom UIs)."
    override val parametersJson = """
        {"type":"object","properties":{"x":{"type":"integer"},"y":{"type":"integer"},"tab_id":{"type":"string"}},"required":["x","y"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val x = args["x"]?.toString()?.toIntOrNull() ?: return ToolExecutionResult("x required", success = false)
        val y = args["y"]?.toString()?.toIntOrNull() ?: return ToolExecutionResult("y required", success = false)
        val tabId = args["tab_id"]?.toString() ?: context.activeTabId
        val result = browserController.clickAt(x, y, tabId)
        context.browserActions += AgentAction(type = "click_xy", target = "$x,$y", reasoning = "Agent clicked at coordinates")
        return ToolExecutionResult(result ?: "clicked")
    }
}

class BrowserConsoleTool(private val browserController: BrowserController) : AgentTool {
    override val name = "browser_console"
    override val description = "Evaluate JavaScript in the page and return the result."
    override val parametersJson = """
        {"type":"object","properties":{"expression":{"type":"string"},"tab_id":{"type":"string"}},"required":["expression"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val expression = args["expression"]?.toString().orEmpty()
        val tabId = args["tab_id"]?.toString() ?: context.activeTabId
        val result = browserController.evaluateScript(expression, tabId)
        return ToolExecutionResult(result ?: "(null)")
    }
}

class BrowserVisionTool(
    private val browserController: BrowserController,
    private val llmApi: com.autobrowse.android.data.remote.LlmApiService,
    private val repository: com.autobrowse.android.data.repository.AutobrowseRepository,
) : AgentTool {
    override val name = "browser_vision"
    override val description = "Capture a screenshot and analyze the visible page with vision (cloud LLM) or describe capture (local)."
    override val parametersJson = """
        {"type":"object","properties":{"question":{"type":"string"},"tab_id":{"type":"string"}},"required":["question"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val question = args["question"]?.toString().orEmpty()
        val tabId = args["tab_id"]?.toString() ?: context.activeTabId
        val base64 = browserController.captureScreenshotBase64(tabId)
            ?: return ToolExecutionResult("Could not capture screenshot", success = false)
        val dataUrl = "data:image/jpeg;base64,$base64"
        context.pendingVisionImages += dataUrl
        context.extractedData["last_screenshot"] = "captured"

        val config = repository.getLlmConfig()
        return if (config.provider == com.autobrowse.android.domain.model.LlmProvider.REMOTE) {
            val analysis = llmApi.chat(
                config = config,
                systemPrompt = "Analyze the browser screenshot and answer precisely.",
                userPrompt = question,
                attachmentPayload = com.autobrowse.android.domain.model.AttachmentPayload(
                    attachments = listOf(
                        com.autobrowse.android.domain.model.ProcessedAttachment(
                            attachment = com.autobrowse.android.domain.model.StoredAttachment(
                                id = "screenshot",
                                type = com.autobrowse.android.domain.model.AttachmentType.IMAGE,
                                fileName = "screenshot.jpg",
                                localPath = "",
                                mimeType = "image/jpeg",
                            ),
                            description = "Browser screenshot",
                            imageBase64Parts = listOf(dataUrl),
                        ),
                    ),
                ),
            )
            ToolExecutionResult("Vision analysis:\n$analysis")
        } else {
            ToolExecutionResult(
                "Screenshot captured (${base64.length} bytes base64). Local model vision pending — " +
                    "use browser_snapshot for text. Question: $question",
            )
        }
    }
}