package com.autobrowse.android.browser

import com.autobrowse.android.domain.model.BrowserTab
import com.autobrowse.android.domain.model.BrowserWindowFrame
import com.autobrowse.android.domain.model.BrowserWindowLayout
import com.autobrowse.android.domain.model.BrowserWindowState
import com.autobrowse.android.domain.model.withFrame

/**
 * In-memory sole authority for floating window geometry.
 * Database is write-behind storage only — never a live source after hydration.
 */
object FloatingWindowEngine {

    fun hydrateFrame(tabId: String, dbTab: BrowserTab?, existing: BrowserWindowFrame?): BrowserWindowFrame {
        if (existing != null) return existing
        return if (dbTab != null) BrowserWindowFrame.fromTab(dbTab) else {
            BrowserWindowFrame(layout = BrowserWindowLayout.defaultForIndex(0))
        }
    }

    fun syncTabMetadata(
        dbTab: BrowserTab,
        existingTab: BrowserTab?,
        frame: BrowserWindowFrame,
    ): BrowserTab = BrowserTab(
        id = dbTab.id,
        url = dbTab.url,
        title = dbTab.title,
        status = dbTab.status,
        isAgentControlled = dbTab.isAgentControlled,
        zIndex = existingTab?.zIndex ?: dbTab.zIndex,
        desktopMode = dbTab.desktopMode,
    ).withFrame(frame)

    fun updateFrame(
        frames: Map<String, BrowserWindowFrame>,
        tabId: String,
        transform: (BrowserWindowFrame) -> BrowserWindowFrame,
    ): Map<String, BrowserWindowFrame> {
        val current = frames[tabId] ?: return frames
        return frames + (tabId to transform(current))
    }

    fun commitLayout(
        frames: Map<String, BrowserWindowFrame>,
        tabId: String,
        layout: BrowserWindowLayout,
        fallbackTab: BrowserTab? = null,
    ): Map<String, BrowserWindowFrame> {
        val current = frames[tabId]
            ?: fallbackTab?.let { BrowserWindowFrame.fromTab(it) }
            ?: return frames
        return frames + (tabId to current.copy(layout = layout, isManipulating = false))
    }

    fun toggleMaximize(
        frames: Map<String, BrowserWindowFrame>,
        tabId: String,
    ): Map<String, BrowserWindowFrame> = updateFrame(frames, tabId) { frame ->
        when (frame.windowState) {
            BrowserWindowState.MAXIMIZED, BrowserWindowState.MINIMIZED -> frame.copy(
                windowState = BrowserWindowState.NORMAL,
                layout = frame.savedLayout ?: frame.layout,
                savedLayout = null,
                isManipulating = false,
            )
            BrowserWindowState.NORMAL -> frame.copy(
                windowState = BrowserWindowState.MAXIMIZED,
                savedLayout = frame.layout,
                layout = BrowserWindowLayout.maximized(),
                isManipulating = false,
            )
        }
    }

    fun minimize(
        frames: Map<String, BrowserWindowFrame>,
        tabId: String,
    ): Map<String, BrowserWindowFrame> = updateFrame(frames, tabId) { frame ->
        if (frame.windowState == BrowserWindowState.MINIMIZED) {
            frame.copy(
                windowState = BrowserWindowState.NORMAL,
                layout = frame.savedLayout ?: frame.layout,
                savedLayout = null,
                isManipulating = false,
            )
        } else {
            val restoreLayout = when (frame.windowState) {
                BrowserWindowState.MAXIMIZED -> frame.savedLayout ?: frame.layout
                else -> frame.layout
            }
            frame.copy(
                windowState = BrowserWindowState.MINIMIZED,
                savedLayout = restoreLayout,
                layout = restoreLayout,
                isManipulating = false,
            )
        }
    }

    fun removeFrame(
        frames: Map<String, BrowserWindowFrame>,
        tabId: String,
    ): Map<String, BrowserWindowFrame> = frames - tabId

    fun addFrameForTab(
        frames: Map<String, BrowserWindowFrame>,
        tab: BrowserTab,
    ): Map<String, BrowserWindowFrame> = if (frames.containsKey(tab.id)) {
        frames
    } else {
        frames + (tab.id to BrowserWindowFrame.fromTab(tab))
    }
}