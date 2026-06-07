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
            loadWithOverviewMode = false
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            textZoom = (100 * VirtualDisplayConfig.CONTENT_ZOOM).toInt()
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
        }
        @Suppress("DEPRECATION")
        webView.setInitialScale(100)
    }

    fun applyVirtualViewport(webView: WebView) {
        val script = """
            (function() {
                var width = ${VirtualDisplayConfig.WIDTH};
                var meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.setAttribute('name', 'viewport');
                    document.head.appendChild(meta);
                }
                meta.setAttribute('content', 'width=' + width + ', initial-scale=${VirtualDisplayConfig.CONTENT_ZOOM}');
                if (document.documentElement) {
                    document.documentElement.style.zoom = '${VirtualDisplayConfig.CONTENT_ZOOM}';
                    document.documentElement.style.transform = '';
                    document.documentElement.style.minWidth = '';
                }
                if (document.body) {
                    document.body.style.zoom = '${VirtualDisplayConfig.CONTENT_ZOOM}';
                    document.body.style.transform = '';
                    document.body.style.minWidth = '';
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }
}