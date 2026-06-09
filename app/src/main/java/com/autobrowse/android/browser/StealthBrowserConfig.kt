package com.autobrowse.android.browser

import android.os.Build
import android.webkit.WebView

/**
 * kdriver-inspired stealth patterns adapted for Android WebView (no CDP).
 * Reduces bot signals before CAPTCHAs trigger.
 */
object StealthBrowserConfig {
    const val ANDROID_CHROME_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Mobile Safari/537.36"

    fun apply(webView: WebView, useAndroidFingerprint: Boolean) {
        if (useAndroidFingerprint) {
            webView.settings.userAgentString = buildAndroidUserAgent()
        }
    }

    fun buildAndroidUserAgent(): String {
        val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Pixel 9 Pro"
        val release = Build.VERSION.RELEASE?.takeIf { it.isNotBlank() } ?: "14"
        return "Mozilla/5.0 (Linux; Android $release; $model) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Mobile Safari/537.36"
    }

    val STEALTH_INJECTION = """
        (function() {
            try {
                Object.defineProperty(navigator, 'webdriver', { get: function() { return undefined; }, configurable: true });
            } catch (e) {}
            try {
                if (!window.chrome) window.chrome = { runtime: {} };
            } catch (e) {}
            try {
                Object.defineProperty(navigator, 'plugins', {
                    get: function() { return [1, 2, 3, 4, 5]; },
                    configurable: true
                });
            } catch (e) {}
            try {
                Object.defineProperty(navigator, 'languages', {
                    get: function() { return ['en-US', 'en']; },
                    configurable: true
                });
            } catch (e) {}
            try {
                if (!navigator.maxTouchPoints || navigator.maxTouchPoints < 1) {
                    Object.defineProperty(navigator, 'maxTouchPoints', { get: function() { return 5; }, configurable: true });
                }
            } catch (e) {}
        })();
    """.trimIndent()
}