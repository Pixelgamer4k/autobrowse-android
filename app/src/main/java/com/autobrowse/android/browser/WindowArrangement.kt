package com.autobrowse.android.browser

import com.autobrowse.android.domain.model.BrowserWindowLayout
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Computes fraction-based layouts for multi-window visibility.
 * Focus tab is larger and centered; background tabs shrink and spread out.
 */
object WindowArrangement {

    fun arrange(tabIds: List<String>, focusTabId: String): Map<String, BrowserWindowLayout> {
        if (tabIds.isEmpty()) return emptyMap()
        val focus = focusTabId.takeIf { it in tabIds } ?: tabIds.last()
        val backgrounds = tabIds.filter { it != focus }
        val count = tabIds.size

        val layouts = mutableMapOf<String, BrowserWindowLayout>()

        when (count) {
            1 -> layouts[focus] = slot(0.06f, 0.06f, 0.78f)
            2 -> {
                layouts[focus] = slot(0.04f, 0.08f, 0.58f)
                backgrounds.firstOrNull()?.let { layouts[it] = slot(0.50f, 0.12f, 0.46f) }
            }
            3 -> {
                layouts[focus] = slot(0.22f, 0.10f, 0.56f)
                backgrounds.getOrNull(0)?.let { layouts[it] = slot(0.02f, 0.08f, 0.42f) }
                backgrounds.getOrNull(1)?.let { layouts[it] = slot(0.56f, 0.08f, 0.40f) }
            }
            4 -> {
                layouts[focus] = slot(0.28f, 0.24f, 0.54f)
                val slots = listOf(
                    slot(0.02f, 0.04f, 0.40f),
                    slot(0.56f, 0.04f, 0.40f),
                    slot(0.02f, 0.50f, 0.40f),
                )
                backgrounds.take(3).forEachIndexed { index, id -> layouts[id] = slots[index] }
            }
            else -> {
                val focusWidth = when {
                    count >= 7 -> 0.76f
                    count >= 5 -> 0.70f
                    else -> 0.64f
                }
                layouts[focus] = slot(
                    x = (1f - focusWidth) / 2f,
                    y = 0.10f,
                    width = focusWidth,
                )
                val bgWidth = (0.40f - (count - 5).coerceAtLeast(0) * 0.015f)
                    .coerceIn(BrowserWindowLayout.MIN_FRACTION, 0.46f)
                backgroundSlots(backgrounds.size, bgWidth).forEachIndexed { index, layout ->
                    layouts[backgrounds[index]] = layout
                }
            }
        }

        return layouts.mapValues { (_, layout) -> layout.clamped() }
    }

    fun resize(
        widthFraction: Float,
        offsetX: Float?,
        offsetY: Float?,
        current: BrowserWindowLayout?,
    ): BrowserWindowLayout {
        val base = current ?: BrowserWindowLayout.defaultForIndex(0)
        val width = widthFraction.coerceIn(BrowserWindowLayout.MIN_FRACTION, BrowserWindowLayout.MAX_FRACTION)
        return base.copy(
            widthFraction = width,
            offsetX = offsetX ?: base.offsetX,
            offsetY = offsetY ?: base.offsetY,
        ).clamped()
    }

    private fun slot(x: Float, y: Float, width: Float): BrowserWindowLayout =
        BrowserWindowLayout(
            offsetX = x,
            offsetY = y,
            widthFraction = width,
            heightFraction = width,
        )

    private fun backgroundSlots(count: Int, width: Float): List<BrowserWindowLayout> {
        if (count == 0) return emptyList()
        val cols = ceil(sqrt(count.toDouble())).toInt().coerceAtLeast(2)
        val rows = ceil(count / cols.toDouble()).toInt()
        val xGap = 0.02f
        val yGap = 0.03f
        val usableWidth = 1f - xGap * 2f
        val cellW = (usableWidth / cols).coerceAtMost(width + 0.04f)
        val maxX = (1f - cellW).coerceAtLeast(0f)

        return List(count) { index ->
            val col = index % cols
            val row = index / cols
            val x = (xGap + col * (usableWidth / cols)).coerceIn(0f, maxX)
            val y = (yGap + row * (0.46f / rows)).coerceIn(0f, 0.52f)
            val edgeBoost = if (index % 2 == 0) 0f else 0.02f
            slot(
                x = (x + edgeBoost).coerceAtMost(maxX),
                y = y,
                width = width.coerceAtMost(cellW),
            )
        }
    }
}