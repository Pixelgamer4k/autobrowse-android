package com.autobrowse.android.domain.model

enum class BrowserWindowState {
    NORMAL,
    MINIMIZED,
    MAXIMIZED,
}

data class BrowserWindowLayout(
    val offsetX: Float = 0.05f,
    val offsetY: Float = 0.05f,
    val widthFraction: Float = 0.68f,
    val heightFraction: Float = 0.58f,
) {
    companion object {
        const val MIN_FRACTION = 0.22f
        const val MAX_FRACTION = 1f
        const val TITLE_BAR_HEIGHT_DP = 44f

        fun defaultForIndex(index: Int): BrowserWindowLayout {
            val step = 0.07f * index.coerceAtMost(6)
            return BrowserWindowLayout(
                offsetX = (0.04f + step).coerceAtMost(0.32f),
                offsetY = (0.04f + step).coerceAtMost(0.32f),
                widthFraction = 0.68f,
                heightFraction = 0.58f,
            )
        }

        fun maximized(): BrowserWindowLayout = BrowserWindowLayout(
            offsetX = 0f,
            offsetY = 0f,
            widthFraction = 1f,
            heightFraction = 1f,
        )
    }

    fun clamped(): BrowserWindowLayout = copy(
        offsetX = offsetX.coerceIn(0f, 1f - MIN_FRACTION),
        offsetY = offsetY.coerceIn(0f, 1f - MIN_FRACTION),
        widthFraction = widthFraction.coerceIn(MIN_FRACTION, MAX_FRACTION),
        heightFraction = heightFraction.coerceIn(MIN_FRACTION, MAX_FRACTION),
    ).let { layout ->
        layout.copy(
            offsetX = layout.offsetX.coerceAtMost(1f - layout.widthFraction),
            offsetY = layout.offsetY.coerceAtMost(1f - layout.heightFraction),
        )
    }
}