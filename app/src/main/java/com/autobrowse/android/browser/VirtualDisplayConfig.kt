package com.autobrowse.android.browser

object VirtualDisplayConfig {
    const val WIDTH = 1280
    const val HEIGHT = 960
    const val ASPECT_RATIO = 4f / 3f
    /** Extra zoom so pages read larger inside floating windows. */
    const val CONTENT_ZOOM = 1.5f

    fun scaleForViewport(viewportWidthPx: Float, viewportHeightPx: Float): Float {
        if (viewportWidthPx <= 0f || viewportHeightPx <= 0f) return CONTENT_ZOOM
        return maxOf(viewportWidthPx / WIDTH, viewportHeightPx / HEIGHT) * CONTENT_ZOOM
    }
}