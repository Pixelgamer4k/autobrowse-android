package com.autobrowse.android.browser

import android.webkit.WebSettings
import android.webkit.WebView

object DesktopBrowserConfig {
    const val DESKTOP_VIEWPORT_WIDTH = 1280

    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/122.0.0.0 Safari/537.36"

    fun apply(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            userAgentString = DESKTOP_USER_AGENT
            useWideViewPort = true
            loadWithOverviewMode = false
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            textZoom = 100
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
        }
    }

    @Suppress("DEPRECATION")
    fun fitToWindow(webView: WebView) {
        val width = webView.width
        if (width <= 0) return

        val scale = (width.toFloat() / DESKTOP_VIEWPORT_WIDTH).coerceIn(0.15f, 1.25f)
        val scalePercent = (scale * 100f).toInt().coerceIn(15, 125)
        webView.setInitialScale(scalePercent)

        val script = """
            (function() {
                var scale = $scale;
                var meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = 'viewport';
                    if (document.head) document.head.appendChild(meta);
                }
                meta.setAttribute(
                    'content',
                    'width=$DESKTOP_VIEWPORT_WIDTH, initial-scale=' + scale +
                    ', minimum-scale=0.15, maximum-scale=3.0, user-scalable=yes'
                );
                var doc = document.documentElement;
                if (doc) {
                    doc.style.minWidth = '0';
                    doc.style.width = 'auto';
                    doc.style.zoom = scale;
                }
                if (document.body) {
                    document.body.style.minWidth = '0';
                    document.body.style.width = 'auto';
                    document.body.style.zoom = scale;
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }
}