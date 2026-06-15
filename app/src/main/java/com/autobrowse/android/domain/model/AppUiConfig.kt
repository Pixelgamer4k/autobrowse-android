package com.autobrowse.android.domain.model

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class AppUiConfig(
    /** Internal WebView render scale (0.25 = lighter, 1.5 = sharper). */
    val resolutionScale: Float = 1f,
    /** Max agent reasoning turns per user message (5–50). */
    val maxAgentIterations: Int = 20,
    /** In-app theme override. SYSTEM follows the device setting. */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
) {
    fun coercedScale(): Float = resolutionScale.coerceIn(0.25f, 1.5f)

    fun coercedMaxIterations(): Int = maxAgentIterations.coerceIn(5, 50)
}
