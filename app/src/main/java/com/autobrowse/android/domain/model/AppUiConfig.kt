package com.autobrowse.android.domain.model

data class AppUiConfig(
    /** Internal WebView render scale (0.75 = lighter, 1.5 = sharper). */
    val resolutionScale: Float = 1f,
    /** Max agent reasoning turns per user message (5–50). */
    val maxAgentIterations: Int = 20,
) {
    fun coercedScale(): Float = resolutionScale.coerceIn(0.75f, 1.5f)

    fun coercedMaxIterations(): Int = maxAgentIterations.coerceIn(5, 50)
}