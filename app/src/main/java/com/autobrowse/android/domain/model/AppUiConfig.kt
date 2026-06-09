package com.autobrowse.android.domain.model

data class AppUiConfig(
    /** Internal WebView render scale (0.75 = lighter, 1.5 = sharper). */
    val resolutionScale: Float = 1f,
) {
    fun coercedScale(): Float = resolutionScale.coerceIn(0.75f, 1.5f)
}