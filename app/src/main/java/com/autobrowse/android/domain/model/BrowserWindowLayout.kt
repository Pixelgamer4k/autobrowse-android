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
        const val CONTENT_ASPECT_RATIO = 4f / 3f

        fun contentHeightPx(widthPx: Float): Float = widthPx / CONTENT_ASPECT_RATIO

        fun totalHeightPx(widthPx: Float, titleBarHeightPx: Float): Float =
            contentHeightPx(widthPx) + titleBarHeightPx

        fun heightFractionForWidth(
            widthFraction: Float,
            canvasWidthPx: Float,
            canvasHeightPx: Float,
            titleBarHeightPx: Float,
        ): Float {
            if (canvasWidthPx <= 0f || canvasHeightPx <= 0f) return widthFraction
            val widthPx = widthFraction * canvasWidthPx
            return totalHeightPx(widthPx, titleBarHeightPx) / canvasHeightPx
        }

        fun defaultForIndex(index: Int): BrowserWindowLayout {
            val step = 0.07f * index.coerceAtMost(6)
            return BrowserWindowLayout(
                offsetX = (0.04f + step).coerceAtMost(0.32f),
                offsetY = (0.04f + step).coerceAtMost(0.32f),
                widthFraction = 0.68f,
                heightFraction = 0.68f,
            )
        }

        fun maximized(): BrowserWindowLayout = BrowserWindowLayout(
            offsetX = 0f,
            offsetY = 0f,
            widthFraction = 1f,
            heightFraction = 1f,
        )
    }

    fun withAspectRatio(
        canvasWidthPx: Float,
        canvasHeightPx: Float,
        titleBarHeightPx: Float,
    ): BrowserWindowLayout = copy(
        heightFraction = heightFractionForWidth(widthFraction, canvasWidthPx, canvasHeightPx, titleBarHeightPx),
    )

    fun clamped(
        canvasWidthPx: Float = 0f,
        canvasHeightPx: Float = 0f,
        titleBarHeightPx: Float = 0f,
    ): BrowserWindowLayout {
        val widthClamped = widthFraction.coerceIn(MIN_FRACTION, MAX_FRACTION)
        val withRatio = if (canvasWidthPx > 0f && canvasHeightPx > 0f) {
            copy(widthFraction = widthClamped).withAspectRatio(canvasWidthPx, canvasHeightPx, titleBarHeightPx)
        } else {
            copy(widthFraction = widthClamped)
        }
        return withRatio.copy(
            offsetX = offsetX.coerceIn(0f, 1f - MIN_FRACTION),
            offsetY = offsetY.coerceIn(0f, 1f - MIN_FRACTION),
            heightFraction = withRatio.heightFraction.coerceIn(MIN_FRACTION, MAX_FRACTION),
        ).let { layout ->
            layout.copy(
                offsetX = layout.offsetX.coerceAtMost(1f - layout.widthFraction),
                offsetY = layout.offsetY.coerceAtMost(1f - layout.heightFraction),
            )
        }
    }
}