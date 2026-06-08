package com.autobrowse.android.browser

import com.autobrowse.android.domain.model.BrowserWindowLayout
import com.autobrowse.android.domain.model.BrowserWindowState

data class WindowInfo(
    val tabId: String,
    val title: String,
    val url: String,
    val isActive: Boolean,
    val zIndex: Int,
    val windowState: BrowserWindowState,
    val layout: BrowserWindowLayout,
)

interface WindowManager {
    suspend fun arrangeWindows(focusTabId: String, tabIds: List<String>? = null): List<WindowInfo>
    suspend fun resizeWindow(
        tabId: String,
        widthFraction: Float,
        offsetX: Float? = null,
        offsetY: Float? = null,
    ): WindowInfo
    suspend fun focusWindow(tabId: String): WindowInfo
    fun listWindows(): List<WindowInfo>
}

class AndroidWindowManager : WindowManager {
    var arrangeHandler: suspend (String, List<String>?) -> List<WindowInfo> = { _, _ ->
        throw IllegalStateException("WindowManager not connected to UI")
    }
    var resizeHandler: suspend (String, Float, Float?, Float?) -> WindowInfo = { _, _, _, _ ->
        throw IllegalStateException("WindowManager not connected to UI")
    }
    var focusHandler: suspend (String) -> WindowInfo = {
        throw IllegalStateException("WindowManager not connected to UI")
    }
    var listHandler: () -> List<WindowInfo> = { emptyList() }

    override suspend fun arrangeWindows(focusTabId: String, tabIds: List<String>?): List<WindowInfo> =
        arrangeHandler(focusTabId, tabIds)

    override suspend fun resizeWindow(
        tabId: String,
        widthFraction: Float,
        offsetX: Float?,
        offsetY: Float?,
    ): WindowInfo = resizeHandler(tabId, widthFraction, offsetX, offsetY)

    override suspend fun focusWindow(tabId: String): WindowInfo = focusHandler(tabId)

    override fun listWindows(): List<WindowInfo> = listHandler()
}