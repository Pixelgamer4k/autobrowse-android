package com.autobrowse.android.browser

object VirtualDisplayConfig {
    private const val BASE_WIDTH = 1280
    private const val BASE_HEIGHT = 960
    const val ASPECT_RATIO = 4f / 3f

    /** User-controlled internal render scale from Settings (0.25–1.5). */
    var resolutionScale: Float = 1f

    val WIDTH: Int
        get() = (BASE_WIDTH * resolutionScale.coerceIn(0.25f, 1.5f)).toInt()

    val HEIGHT: Int
        get() = (BASE_HEIGHT * resolutionScale.coerceIn(0.25f, 1.5f)).toInt()

    fun scaleForViewport(viewportWidthPx: Float, viewportHeightPx: Float): Float {
        if (viewportWidthPx <= 0f || viewportHeightPx <= 0f) return 1f
        return maxOf(viewportWidthPx / WIDTH, viewportHeightPx / HEIGHT)
    }
}