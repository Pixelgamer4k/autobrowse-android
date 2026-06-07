package com.autobrowse.android.domain.model

data class BrowserWindowLayout(
    val offsetX: Float = 0.04f,
    val offsetY: Float = 0.04f,
    val widthFraction: Float = 0.92f,
    val heightFraction: Float = 0.88f,
) {
    companion object {
        const val MIN_FRACTION = 0.28f
        const val MAX_FRACTION = 1f

        fun defaultForIndex(index: Int): BrowserWindowLayout {
            val step = 0.04f * index.coerceAtMost(4)
            return BrowserWindowLayout(
                offsetX = 0.04f + step,
                offsetY = 0.04f + step,
                widthFraction = (0.92f - step).coerceAtLeast(MIN_FRACTION),
                heightFraction = (0.88f - step).coerceAtLeast(MIN_FRACTION),
            )
        }
    }

    fun clamped(): BrowserWindowLayout = copy(
        offsetX = offsetX.coerceIn(0f, 1f - MIN_FRACTION),
        offsetY = offsetY.coerceIn(0f, 1f - MIN_FRACTION),
        widthFraction = widthFraction.coerceIn(MIN_FRACTION, MAX_FRACTION),
        heightFraction = heightFraction.coerceIn(MIN_FRACTION, MAX_FRACTION),
    )
}