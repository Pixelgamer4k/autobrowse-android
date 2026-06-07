package com.autobrowse.android.browser

import com.autobrowse.android.domain.model.BrowserTab
import com.autobrowse.android.domain.model.BrowserWindowFrame
import com.autobrowse.android.domain.model.BrowserWindowLayout
import com.autobrowse.android.domain.model.BrowserWindowState
import com.autobrowse.android.domain.model.withFrame

/**
 * In-memory authority for floating window geometry.
 * Database sync must never overwrite live frames — only hydrate on first sight.
 */
object FloatingWindowEngine {

    data class MergeResult(
        val tabs: List<BrowserTab>,
        val frames: Map<String, BrowserWindowFrame>,
    )

    fun mergeDbTabs(
        dbTabs: List<BrowserTab>,
        frames: Map<String, BrowserWindowFrame>,
    ): MergeResult {
        val nextFrames = frames.toMutableMap()
        val mergedTabs = dbTabs.map { dbTab ->
            val frame = nextFrames[dbTab.id] ?: BrowserWindowFrame.fromTab(dbTab).also {
                nextFrames[dbTab.id] = it
            }
            dbTab.withFrame(frame)
        }
        val liveIds = dbTabs.map { it.id }.toSet()
        nextFrames.keys.retainAll(liveIds)
        return MergeResult(tabs = mergedTabs, frames = nextFrames)
    }

    fun updateFrame(
        frames: Map<String, BrowserWindowFrame>,
        tabId: String,
        transform: (BrowserWindowFrame) -> BrowserWindowFrame,
    ): Map<String, BrowserWindowFrame> {
        val current = frames[tabId] ?: return frames
        return frames + (tabId to transform(current))
    }

    fun moveFrame(
        frames: Map<String, BrowserWindowFrame>,
        tabId: String,
        layout: BrowserWindowLayout,
        manipulating: Boolean = true,
    ): Map<String, BrowserWindowFrame> = updateFrame(frames, tabId) { frame ->
        frame.copy(
            layout = layout.clamped(),
            windowState = BrowserWindowState.NORMAL,
            isManipulating = manipulating,
        )
    }

    fun resizeFrame(
        frames: Map<String, BrowserWindowFrame>,
        tabId: String,
        layout: BrowserWindowLayout,
        manipulating: Boolean = true,
    ): Map<String, BrowserWindowFrame> = moveFrame(frames, tabId, layout, manipulating)

    fun endManipulation(
        frames: Map<String, BrowserWindowFrame>,
        tabId: String,
    ): Map<String, BrowserWindowFrame> = updateFrame(frames, tabId) { frame ->
        frame.copy(isManipulating = false)
    }

    fun toggleMaximize(
        frames: Map<String, BrowserWindowFrame>,
        tabId: String,
    ): Map<String, BrowserWindowFrame> = updateFrame(frames, tabId) { frame ->
        when (frame.windowState) {
            BrowserWindowState.MAXIMIZED, BrowserWindowState.MINIMIZED -> frame.copy(
                windowState = BrowserWindowState.NORMAL,
                layout = frame.savedLayout?.clamped() ?: frame.layout,
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
            return@updateFrame frame.copy(
                windowState = BrowserWindowState.NORMAL,
                layout = frame.savedLayout?.clamped() ?: frame.layout,
                savedLayout = null,
            )
        }
        val restoreLayout = when (frame.windowState) {
            BrowserWindowState.MAXIMIZED -> frame.savedLayout ?: frame.layout
            else -> frame.layout
        }
        frame.copy(
            windowState = BrowserWindowState.MINIMIZED,
            savedLayout = restoreLayout,
            layout = restoreLayout.clamped(),
            isManipulating = false,
        )
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