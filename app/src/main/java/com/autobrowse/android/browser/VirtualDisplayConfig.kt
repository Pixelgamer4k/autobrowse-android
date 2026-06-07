package com.autobrowse.android.browser

object VirtualDisplayConfig {
    const val WIDTH = 1280
    const val HEIGHT = 720

    fun scaleForViewport(viewportWidthPx: Float): Float {
        if (viewportWidthPx <= 0f) return 1f
        return viewportWidthPx / WIDTH
    }

    fun scaledHeightPx(viewportWidthPx: Float): Float = HEIGHT * scaleForViewport(viewportWidthPx)
}