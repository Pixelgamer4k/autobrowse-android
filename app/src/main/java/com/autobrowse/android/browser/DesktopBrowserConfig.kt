package com.autobrowse.android.browser

import android.webkit.WebSettings
import android.webkit.WebView

object DesktopBrowserConfig {
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
        @Suppress("DEPRECATION")
        webView.setInitialScale(0)
    }

    fun clearViewportOverrides(webView: WebView) {
        val script = """
            (function() {
                var meta = document.querySelector('meta[name="viewport"]');
                if (!meta) return;
                var content = meta.getAttribute('content') || '';
                if (
                    content.indexOf('width=') >= 0 &&
                    content.indexOf('device-width') < 0
                ) {
                    meta.setAttribute('content', 'width=device-width, initial-scale=1.0');
                }
                if (document.documentElement) {
                    document.documentElement.style.zoom = '';
                    document.documentElement.style.transform = '';
                    document.documentElement.style.minWidth = '';
                }
                if (document.body) {
                    document.body.style.zoom = '';
                    document.body.style.transform = '';
                    document.body.style.minWidth = '';
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }
}