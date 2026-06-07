package com.autobrowse.android.agent.tools

import com.autobrowse.android.browser.BrowserController
import com.autobrowse.android.domain.model.AgentAction
import java.net.URLEncoder

class BrowserSearchTool(
    private val browserController: BrowserController,
) : AgentTool {
    override val name = "browser_search"
    override val description =
        "Search a website using direct search URLs (RELIABLE). Prefer this over typing in search boxes. " +
            "Sites: youtube, google, bing, duckduckgo, reddit, amazon, twitter."
    override val parametersJson = """
        {"type":"object","properties":{
            "query":{"type":"string","description":"Search query"},
            "site":{"type":"string","description":"youtube, google, bing, duckduckgo, reddit, amazon, twitter (default: google)"},
            "tab_id":{"type":"string"}
        },"required":["query"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val query = args["query"]?.toString()?.trim().orEmpty()
        if (query.isBlank()) return ToolExecutionResult("query is required", success = false)
        val site = args["site"]?.toString()?.trim()?.lowercase().orEmpty().ifBlank { "google" }
        val tabId = args["tab_id"]?.toString() ?: context.activeTabId
        val url = buildSearchUrl(site, query)
        browserController.loadUrl(url, tabId)
        context.browserActions += AgentAction(
            type = "navigate",
            target = url,
            reasoning = "Direct search on $site for: $query",
        )
        context.extractedData["search_query"] = query
        context.extractedData["search_site"] = site
        context.extractedData["search_url"] = url
        return ToolExecutionResult(
            "Searching $site for \"$query\"\nNavigated to: $url\nWait for results then browser_snapshot to verify.",
        )
    }

    companion object {
        fun buildSearchUrl(site: String, query: String): String {
            val encoded = URLEncoder.encode(query, Charsets.UTF_8.name()).replace("+", "%20")
            return when (site.lowercase()) {
                "youtube", "yt" -> "https://www.youtube.com/results?search_query=$encoded"
                "google" -> "https://www.google.com/search?q=$encoded"
                "bing" -> "https://www.bing.com/search?q=$encoded"
                "duckduckgo", "ddg" -> "https://duckduckgo.com/?q=$encoded"
                "reddit" -> "https://www.reddit.com/search/?q=$encoded"
                "amazon" -> "https://www.amazon.com/s?k=$encoded"
                "twitter", "x" -> "https://x.com/search?q=$encoded&src=typed_query"
                else -> "https://www.google.com/search?q=$encoded"
            }
        }
    }
}

class BrowserWaitTool(
    private val browserController: BrowserController,
) : AgentTool {
    override val name = "browser_wait"
    override val description = "Wait for page load and JS rendering after navigation or clicks."
    override val parametersJson = """
        {"type":"object","properties":{"milliseconds":{"type":"integer","description":"Wait time (default 2000)"},"tab_id":{"type":"string"}}}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val ms = args["milliseconds"]?.toString()?.toLongOrNull() ?: 2000L
        val tabId = args["tab_id"]?.toString() ?: context.activeTabId
        val status = browserController.waitForPageReady(tabId, ms)
        return ToolExecutionResult("Page status: $status (waited ${ms}ms)")
    }
}