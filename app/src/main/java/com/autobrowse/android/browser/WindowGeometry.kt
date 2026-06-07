package com.autobrowse.android.browser

import com.autobrowse.android.domain.model.BrowserWindowFrame
import com.autobrowse.android.domain.model.BrowserWindowLayout
import com.autobrowse.android.domain.model.BrowserWindowState

/**
 * Pure layout math. Display reads never coerce width upward — only resize commits do.
 */
object WindowGeometry {

    data class Canvas(
        val widthPx: Float,
        val heightPx: Float,
        val titleBarPx: Float,
    )

    data class Resolved(
        val layout: BrowserWindowLayout,
        val widthPx: Float,
        val heightPx: Float,
        val xPx: Float,
        val yPx: Float,
    )

    fun resolve(frame: BrowserWindowFrame, canvas: Canvas): Resolved {
        val source = frame.effectiveLayout()
        val widthPx = when (frame.windowState) {
            BrowserWindowState.MINIMIZED -> source.widthFraction * canvas.widthPx
            BrowserWindowState.MAXIMIZED -> canvas.widthPx
            BrowserWindowState.NORMAL -> source.widthFraction * canvas.widthPx
        }
        val heightPx = when (frame.windowState) {
            BrowserWindowState.MINIMIZED -> canvas.titleBarPx.coerceAtLeast(1f)
            BrowserWindowState.MAXIMIZED -> source.heightFraction * canvas.heightPx
            BrowserWindowState.NORMAL -> BrowserWindowLayout.totalHeightPx(widthPx, canvas.titleBarPx)
        }
        val xPx = source.offsetX * canvas.widthPx
        val yPx = source.offsetY * canvas.heightPx
        val layout = BrowserWindowLayout(
            offsetX = source.offsetX,
            offsetY = source.offsetY,
            widthFraction = if (canvas.widthPx > 0f) widthPx / canvas.widthPx else source.widthFraction,
            heightFraction = if (canvas.heightPx > 0f) heightPx / canvas.heightPx else source.heightFraction,
        )
        return Resolved(
            layout = layout,
            widthPx = widthPx,
            heightPx = heightPx,
            xPx = xPx.coerceIn(0f, (canvas.widthPx - widthPx).coerceAtLeast(0f)),
            yPx = yPx.coerceIn(0f, (canvas.heightPx - heightPx).coerceAtLeast(0f)),
        )
    }

    fun layoutFromPixels(
        widthPx: Float,
        offsetX: Float,
        offsetY: Float,
        canvas: Canvas,
        minWidthPx: Float = 0f,
    ): BrowserWindowLayout {
        if (canvas.widthPx <= 0f || canvas.heightPx <= 0f) {
            return BrowserWindowLayout(offsetX = offsetX, offsetY = offsetY)
        }
        val clampedWidthPx = widthPx
            .coerceAtLeast(minWidthPx)
            .coerceAtMost(canvas.widthPx)
        val widthFraction = clampedWidthPx / canvas.widthPx
        val heightFraction = BrowserWindowLayout.heightFractionForWidth(
            widthFraction,
            canvas.widthPx,
            canvas.heightPx,
            canvas.titleBarPx,
        )
        return BrowserWindowLayout(
            offsetX = offsetX.coerceIn(0f, (1f - widthFraction).coerceAtLeast(0f)),
            offsetY = offsetY.coerceIn(0f, (1f - heightFraction).coerceAtLeast(0f)),
            widthFraction = widthFraction,
            heightFraction = heightFraction,
        )
    }

    fun moved(
        base: BrowserWindowLayout,
        widthPx: Float,
        offsetX: Float,
        offsetY: Float,
        canvas: Canvas,
    ): BrowserWindowLayout = layoutFromPixels(
        widthPx = widthPx,
        offsetX = offsetX,
        offsetY = offsetY,
        canvas = canvas,
        minWidthPx = 0f,
    )

    fun resized(
        base: BrowserWindowLayout,
        widthPx: Float,
        canvas: Canvas,
    ): BrowserWindowLayout = layoutFromPixels(
        widthPx = widthPx,
        offsetX = base.offsetX,
        offsetY = base.offsetY,
        canvas = canvas,
        minWidthPx = BrowserWindowLayout.MIN_FRACTION * canvas.widthPx,
    )
}