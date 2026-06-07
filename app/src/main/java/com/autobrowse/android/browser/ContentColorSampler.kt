package com.autobrowse.android.browser

import android.graphics.Bitmap
import android.webkit.WebView
import androidx.compose.ui.graphics.Color
import kotlin.math.sqrt

object ContentColorSampler {

    fun sampleTopBandAsync(webView: WebView, onSampled: (Color) -> Unit) {
        webView.post {
            onSampled(runCatching { sampleTopBand(webView) }.getOrDefault(Color(0xFFE8E8E8)))
        }
    }

    fun sampleTopBand(webView: WebView): Color {
        val viewWidth = webView.width
        val viewHeight = webView.height
        if (viewWidth <= 0 || viewHeight <= 0) return Color(0xFFE8E8E8)

        val sampleWidth = minOf(64, viewWidth)
        val sampleHeight = minOf(16, viewHeight)
        val bitmap = Bitmap.createBitmap(sampleWidth, sampleHeight, Bitmap.Config.RGB_565)
        try {
            val canvas = android.graphics.Canvas(bitmap)
            canvas.save()
            canvas.scale(
                sampleWidth / viewWidth.toFloat(),
                sampleHeight / viewHeight.toFloat(),
            )
            webView.draw(canvas)
            canvas.restore()

            var r = 0L
            var g = 0L
            var b = 0L
            val count = sampleWidth * sampleHeight
            for (x in 0 until sampleWidth) {
                for (y in 0 until sampleHeight) {
                    val pixel = bitmap.getPixel(x, y)
                    r += android.graphics.Color.red(pixel)
                    g += android.graphics.Color.green(pixel)
                    b += android.graphics.Color.blue(pixel)
                }
            }
            return Color(
                red = r.toFloat() / count / 255f,
                green = g.toFloat() / count / 255f,
                blue = b.toFloat() / count / 255f,
            )
        } finally {
            bitmap.recycle()
        }
    }

    /** Pick dot colors that stay readable on top of the sampled page band. */
    fun dotsFromContent(sampled: Color): Color {
        val luminance = sampled.red * 0.299f + sampled.green * 0.587f + sampled.blue * 0.114f
        return if (luminance > 0.58f) {
            Color(
                red = sampled.red * 0.35f,
                green = sampled.green * 0.35f,
                blue = sampled.blue * 0.35f,
                alpha = 0.95f,
            )
        } else {
            val boost = 1.25f
            Color(
                red = (sampled.red * boost).coerceIn(0.55f, 1f),
                green = (sampled.green * boost).coerceIn(0.55f, 1f),
                blue = (sampled.blue * boost).coerceIn(0.55f, 1f),
                alpha = 0.95f,
            )
        }
    }

    fun saturate(color: Color): Color {
        val max = maxOf(color.red, color.green, color.blue)
        val min = minOf(color.red, color.green, color.blue)
        if (max - min < 0.08f) return color
        val delta = max - min
        val saturationBoost = 1.18f
        return Color(
            red = (color.red + (color.red - (max + min) / 2f) * saturationBoost / delta * 0.08f)
                .coerceIn(0f, 1f),
            green = (color.green + (color.green - (max + min) / 2f) * saturationBoost / delta * 0.08f)
                .coerceIn(0f, 1f),
            blue = (color.blue + (color.blue - (max + min) / 2f) * saturationBoost / delta * 0.08f)
                .coerceIn(0f, 1f),
            alpha = color.alpha,
        )
    }

    fun smoothToward(current: Color, target: Color, factor: Float = 0.35f): Color {
        val f = factor.coerceIn(0.1f, 1f)
        return Color(
            red = current.red + (target.red - current.red) * f,
            green = current.green + (target.green - current.green) * f,
            blue = current.blue + (target.blue - current.blue) * f,
            alpha = current.alpha + (target.alpha - current.alpha) * f,
        )
    }

    fun isValid(color: Color): Boolean {
        val sum = color.red + color.green + color.blue
        return sum > 0.05f && !sum.isNaN() && sqrt(color.red * color.red + color.green * color.green + color.blue * color.blue) > 0.08f
    }
}