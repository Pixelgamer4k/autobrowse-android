package com.autobrowse.android.browser

class AndroidTabManager : TabManager {
    var openTabHandler: suspend (String?) -> TabInfo = {
        throw IllegalStateException("TabManager not connected to UI")
    }
    var closeTabHandler: suspend (String?) -> TabInfo? = {
        throw IllegalStateException("TabManager not connected to UI")
    }
    var switchTabHandler: suspend (String) -> TabInfo = {
        throw IllegalStateException("TabManager not connected to UI")
    }
    var listTabsHandler: () -> List<TabInfo> = { emptyList() }
    var activeTabIdHandler: () -> String? = { null }

    override suspend fun openTab(url: String?): TabInfo = openTabHandler(url)
    override suspend fun closeTab(tabId: String?): TabInfo? = closeTabHandler(tabId)
    override suspend fun switchTab(tabId: String): TabInfo = switchTabHandler(tabId)
    override fun listTabs(): List<TabInfo> = listTabsHandler()
    override fun activeTabId(): String? = activeTabIdHandler()
}