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
        const val MIN_FRACTION = 0.10f
        const val MAX_FRACTION = 1f
        const val TITLE_BAR_HEIGHT_DP = 0f
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

        fun fromDisplayedSize(
            widthPx: Float,
            offsetX: Float,
            offsetY: Float,
            canvasWidthPx: Float,
            canvasHeightPx: Float,
            titleBarHeightPx: Float,
        ): BrowserWindowLayout {
            if (canvasWidthPx <= 0f || canvasHeightPx <= 0f) {
                return BrowserWindowLayout(offsetX = offsetX, offsetY = offsetY)
            }
            val widthFraction = (widthPx / canvasWidthPx).coerceIn(0f, MAX_FRACTION)
            val heightFraction = heightFractionForWidth(
                widthFraction,
                canvasWidthPx,
                canvasHeightPx,
                titleBarHeightPx,
            )
            return BrowserWindowLayout(
                offsetX = offsetX.coerceIn(0f, (1f - widthFraction).coerceAtLeast(0f)),
                offsetY = offsetY.coerceIn(0f, (1f - heightFraction).coerceAtLeast(0f)),
                widthFraction = widthFraction,
                heightFraction = heightFraction,
            )
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

    fun normalized(
        canvasWidthPx: Float,
        canvasHeightPx: Float,
        titleBarHeightPx: Float,
    ): BrowserWindowLayout = copy(
        widthFraction = widthFraction.coerceIn(MIN_FRACTION, MAX_FRACTION),
    ).withAspectRatio(canvasWidthPx, canvasHeightPx, titleBarHeightPx)

    fun clamped(
        canvasWidthPx: Float = 0f,
        canvasHeightPx: Float = 0f,
        titleBarHeightPx: Float = 0f,
    ): BrowserWindowLayout {
        val layout = if (canvasWidthPx > 0f && canvasHeightPx > 0f) {
            normalized(canvasWidthPx, canvasHeightPx, titleBarHeightPx)
        } else {
            copy(
                widthFraction = widthFraction.coerceIn(MIN_FRACTION, MAX_FRACTION),
                heightFraction = heightFraction.coerceIn(MIN_FRACTION, MAX_FRACTION),
            )
        }
        return layout.copy(
            offsetX = offsetX.coerceIn(0f, (1f - layout.widthFraction).coerceAtLeast(0f)),
            offsetY = offsetY.coerceIn(0f, (1f - layout.heightFraction).coerceAtLeast(0f)),
        )
    }

    fun clampedOffsetOnly(
        canvasWidthPx: Float,
        canvasHeightPx: Float,
        titleBarHeightPx: Float,
    ): BrowserWindowLayout {
        val layout = normalized(canvasWidthPx, canvasHeightPx, titleBarHeightPx)
        return copy(
            widthFraction = layout.widthFraction,
            heightFraction = layout.heightFraction,
            offsetX = offsetX.coerceIn(0f, (1f - layout.widthFraction).coerceAtLeast(0f)),
            offsetY = offsetY.coerceIn(0f, (1f - layout.heightFraction).coerceAtLeast(0f)),
        )
    }

}