package com.autobrowse.android.agent.tools

import com.autobrowse.android.browser.BrowserController

object BrowserToolHelper {
    val BROWSER_TOOL_PREFIXES = listOf(
        "browser_navigate",
        "browser_search",
        "browser_snapshot",
        "browser_click",
        "browser_type",
        "browser_fill",
        "browser_scroll",
        "browser_press",
        "browser_back",
        "browser_click_xy",
        "browser_wait",
        "browser_tab_",
    )

    fun isBrowserTool(name: String): Boolean =
        BROWSER_TOOL_PREFIXES.any { name == it || name.startsWith(it) }

    suspend fun refreshPageContext(context: ToolExecutionContext, browserController: BrowserController) {
        val tabId = context.activeTabId
        context.pageUrl = browserController.getCurrentUrl(tabId)
        context.pageHtml = browserController.getPageHtml(tabId)
        context.pageText = browserController.getPageText(tabId)
    }

    suspend fun afterBrowserAction(
        context: ToolExecutionContext,
        browserController: BrowserController,
        waitMs: Long = 1500,
    ) {
        browserController.waitForPageReady(context.activeTabId, waitMs)
        refreshPageContext(context, browserController)
    }
}