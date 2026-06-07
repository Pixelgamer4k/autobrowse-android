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

    data class SessionSnapshot(
        val tabs: List<BrowserTab>,
        val frames: Map<String, BrowserWindowFrame>,
    )

    /**
     * Ensures every live tab owns an in-memory frame. Never drops geometry for tabs
     * that are still alive.
     */
    fun reconcileFrames(
        tabs: List<BrowserTab>,
        frames: Map<String, BrowserWindowFrame>,
    ): Map<String, BrowserWindowFrame> {
        val liveIds = tabs.map { it.id }.toSet()
        val result = frames.filterKeys { it in liveIds }.toMutableMap()
        tabs.forEach { tab ->
            if (tab.id !in result) {
                result[tab.id] = BrowserWindowFrame.fromTab(tab)
            }
        }
        return result
    }

    fun frameForTab(
        tabId: String,
        tabs: List<BrowserTab>,
        frames: Map<String, BrowserWindowFrame>,
    ): BrowserWindowFrame {
        frames[tabId]?.let { return it }
        tabs.find { it.id == tabId }?.let { return BrowserWindowFrame.fromTab(it) }
        return BrowserWindowFrame(layout = BrowserWindowLayout.defaultForIndex(0))
    }

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

    /**
     * Merge DB tab rows into UI state without overwriting live in-memory frames.
     */
    fun mergeDbTabs(
        dbTabs: List<BrowserTab>,
        currentTabs: List<BrowserTab>,
        currentFrames: Map<String, BrowserWindowFrame>,
    ): SessionSnapshot {
        val tabsById = currentTabs.associateBy { it.id }
        val frames = reconcileFrames(currentTabs, currentFrames).toMutableMap()
        val liveIds = dbTabs.map { it.id }.toSet()
        frames.keys.retainAll(liveIds)

        val tabs = dbTabs.map { dbTab ->
            val existingTab = tabsById[dbTab.id]
            val frame = hydrateFrame(
                tabId = dbTab.id,
                dbTab = dbTab,
                existing = frames[dbTab.id],
            ).also { frames[dbTab.id] = it }
            syncTabMetadata(dbTab, existingTab, frame)
        }

        return SessionSnapshot(
            tabs = tabs,
            frames = reconcileFrames(tabs, frames),
        )
    }

    fun updateFrame(
        frames: Map<String, BrowserWindowFrame>,
        tabId: String,
        fallbackTab: BrowserTab? = null,
        transform: (BrowserWindowFrame) -> BrowserWindowFrame,
    ): Map<String, BrowserWindowFrame> {
        val current = frames[tabId]
            ?: fallbackTab?.let { BrowserWindowFrame.fromTab(it) }
            ?: return frames
        return frames + (tabId to transform(current))
    }

    fun commitLayout(
        frames: Map<String, BrowserWindowFrame>,
        tabId: String,
        layout: BrowserWindowLayout,
        fallbackTab: BrowserTab? = null,
    ): Map<String, BrowserWindowFrame> = updateFrame(frames, tabId, fallbackTab) { frame ->
        frame.copy(layout = layout, isManipulating = false)
    }

    fun toggleMaximize(
        frames: Map<String, BrowserWindowFrame>,
        tabId: String,
        fallbackTab: BrowserTab? = null,
    ): Map<String, BrowserWindowFrame> = updateFrame(frames, tabId, fallbackTab) { frame ->
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
        fallbackTab: BrowserTab? = null,
    ): Map<String, BrowserWindowFrame> = updateFrame(frames, tabId, fallbackTab) { frame ->
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
    ): Map<String, BrowserWindowFrame> = reconcileFrames(
        tabs = listOf(tab),
        frames = frames + (tab.id to BrowserWindowFrame.fromTab(tab)),
    )
}