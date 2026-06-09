package com.autobrowse.android.agent.tools

import com.autobrowse.android.browser.BrowserController

object BrowserToolHelper {
    fun isBrowserTool(name: String): Boolean = name.startsWith("browser_")

    suspend fun refreshPageContext(context: ToolExecutionContext, browserController: BrowserController) {
        val tabId = context.activeTabId
        context.pageUrl = browserController.getCurrentUrl(tabId)
        context.pageHtml = browserController.getPageHtml(tabId)
        context.pageText = browserController.getPageText(tabId)
        CaptchaTools.refreshCaptchaState(context, browserController)
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