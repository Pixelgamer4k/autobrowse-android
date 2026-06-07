package com.autobrowse.android.browser

data class TabInfo(
    val id: String,
    val url: String,
    val title: String,
    val isActive: Boolean,
)

interface TabManager {
    suspend fun openTab(url: String? = null): TabInfo
    suspend fun closeTab(tabId: String? = null): TabInfo?
    suspend fun switchTab(tabId: String): TabInfo
    fun listTabs(): List<TabInfo>
    fun activeTabId(): String?
}