package com.autobrowse.android.browser

import android.webkit.WebSettings
import android.webkit.WebView

object DesktopBrowserConfig {
    const val DESKTOP_VIEWPORT_WIDTH = 1280

    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/122.0.0.0 Safari/537.36"

    fun viewportScript(viewWidthPx: Int): String {
        val safeWidth = viewWidthPx.coerceAtLeast(1)
        val scale = (safeWidth.toFloat() / DESKTOP_VIEWPORT_WIDTH).coerceIn(0.15f, 1.5f)
        return """
            (function() {
                var meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = 'viewport';
                    if (document.head) document.head.appendChild(meta);
                }
                meta.setAttribute(
                    'content',
                    'width=$DESKTOP_VIEWPORT_WIDTH, initial-scale=$scale, minimum-scale=0.2, maximum-scale=3.0, user-scalable=yes'
                );
                if (document.documentElement) {
                    document.documentElement.style.minWidth = '';
                    document.documentElement.style.width = '';
                }
                if (document.body) {
                    document.body.style.minWidth = '';
                    document.body.style.width = '';
                    document.body.style.zoom = '';
                }
            })();
        """.trimIndent()
    }

    fun apply(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            userAgentString = DESKTOP_USER_AGENT
            useWideViewPort = true
            loadWithOverviewMode = true
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

    fun fitToWindow(webView: WebView) {
        if (webView.width <= 0) return
        webView.evaluateJavascript(viewportScript(webView.width), null)
    }
}