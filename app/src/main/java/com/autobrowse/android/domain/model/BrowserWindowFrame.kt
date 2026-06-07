package com.autobrowse.android.domain.model

data class BrowserWindowFrame(
    val layout: BrowserWindowLayout,
    val windowState: BrowserWindowState = BrowserWindowState.NORMAL,
    val savedLayout: BrowserWindowLayout? = null,
    val isManipulating: Boolean = false,
) {
    fun effectiveLayout(): BrowserWindowLayout = when (windowState) {
        BrowserWindowState.MAXIMIZED -> BrowserWindowLayout.maximized()
        BrowserWindowState.MINIMIZED,
        BrowserWindowState.NORMAL,
        -> layout
    }

    companion object {
        fun fromTab(tab: BrowserTab): BrowserWindowFrame = BrowserWindowFrame(
            layout = tab.layout,
            windowState = tab.windowState,
            savedLayout = tab.savedLayout,
        )
    }
}

fun BrowserTab.withFrame(frame: BrowserWindowFrame): BrowserTab = copy(
    layout = frame.layout,
    windowState = frame.windowState,
    savedLayout = frame.savedLayout,
)