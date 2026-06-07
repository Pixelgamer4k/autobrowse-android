package com.autobrowse.android.browser

import android.webkit.WebSettings
import android.webkit.WebView

object DesktopBrowserConfig {
    const val DESKTOP_VIEWPORT_WIDTH = 1280

    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/122.0.0.0 Safari/537.36"

    const val VIEWPORT_INJECT_SCRIPT = """
        (function() {
            var meta = document.querySelector('meta[name="viewport"]');
            if (!meta) {
                meta = document.createElement('meta');
                meta.name = 'viewport';
                if (document.head) document.head.appendChild(meta);
            }
            meta.content = 'width=$DESKTOP_VIEWPORT_WIDTH, initial-scale=1.0';
            document.documentElement.style.minWidth = '${DESKTOP_VIEWPORT_WIDTH}px';
            if (document.body) {
                document.body.style.minWidth = '${DESKTOP_VIEWPORT_WIDTH}px';
            }
        })();
    """

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
        }
    }
}