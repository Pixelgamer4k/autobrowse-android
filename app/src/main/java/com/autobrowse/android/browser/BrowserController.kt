package com.autobrowse.android.browser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Base64
import android.webkit.WebView
import com.autobrowse.android.domain.model.AgentAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class BrowserController {
    private val webViews = ConcurrentHashMap<String, WebView>()
    private var activeTabId: String? = null

    fun attach(tabId: String, webView: WebView) {
        webViews[tabId] = webView
        if (activeTabId == null) activeTabId = tabId
    }

    fun detach(tabId: String) {
        webViews.remove(tabId)
        if (activeTabId == tabId) {
            activeTabId = webViews.keys.firstOrNull()
        }
    }

    fun setActiveTab(tabId: String) {
        if (webViews.containsKey(tabId)) {
            activeTabId = tabId
        }
    }

    fun getActiveTabId(): String? = activeTabId

    fun listAttachedTabIds(): List<String> = webViews.keys.toList()

    private fun webViewFor(tabId: String?): WebView? =
        tabId?.let { webViews[it] } ?: activeTabId?.let { webViews[it] }

    fun loadUrl(url: String, tabId: String? = null) {
        val normalized = AddressBarNavigation.resolve(url) ?: return
        webViewFor(tabId)?.loadUrl(normalized)
    }

    fun goBack(tabId: String? = null) {
        webViewFor(tabId)?.goBack()
    }

    suspend fun getPageHtml(tabId: String? = null): String? = evaluateJs(
        script = """
        (function() {
            return document.documentElement.outerHTML;
        })();
        """.trimIndent(),
        tabId = tabId,
    )

    suspend fun getPageText(tabId: String? = null): String? = evaluateJs(
        script = """
        (function() {
            return document.body ? document.body.innerText : '';
        })();
        """.trimIndent(),
        tabId = tabId,
    )

    suspend fun getInteractiveSnapshot(tabId: String? = null): JSONObject? {
        val raw = evaluateJs(BrowserSnapshotScript.INTERACTIVE_SNAPSHOT, tabId) ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    fun getCurrentUrl(tabId: String? = null): String? = webViewFor(tabId)?.url

    suspend fun evaluateScript(script: String, tabId: String? = null): String? =
        evaluateJs(script, tabId)

    suspend fun clickRef(ref: String, tabId: String? = null): String? =
        evaluateJs(BrowserSnapshotScript.clickRefScript(ref), tabId)

    suspend fun typeRef(ref: String, text: String, tabId: String? = null): String? =
        evaluateJs(BrowserSnapshotScript.typeRefScript(ref, text), tabId)

    suspend fun clickAt(x: Int, y: Int, tabId: String? = null): String? =
        evaluateJs(BrowserSnapshotScript.clickXYScript(x, y), tabId)

    suspend fun scroll(direction: String, amount: Int = 500, tabId: String? = null): String? =
        evaluateJs(BrowserSnapshotScript.scrollScript(direction, amount), tabId)

    suspend fun pressKey(key: String, tabId: String? = null): String? =
        evaluateJs(BrowserSnapshotScript.pressKeyScript(key), tabId)

    suspend fun captureScreenshotBase64(tabId: String? = null): String? = withContext(Dispatchers.Main) {
        val webView = webViewFor(tabId) ?: return@withContext null
        if (webView.width <= 0 || webView.height <= 0) return@withContext null
        val bitmap = Bitmap.createBitmap(
            webView.width.coerceAtMost(1200),
            webView.height.coerceAtMost(2000),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        webView.draw(canvas)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        bitmap.recycle()
        Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    suspend fun executeActions(actions: List<AgentAction>): List<String> {
        val results = mutableListOf<String>()
        for (action in actions) {
            when (action.type) {
                "fill" -> {
                    val selector = action.target ?: continue
                    val value = action.value ?: ""
                    val escaped = value.replace("\\", "\\\\").replace("'", "\\'")
                    val script = """
                        (function() {
                            var el = document.querySelector('$selector');
                            if (!el) return 'not_found';
                            el.value = '$escaped';
                            el.dispatchEvent(new Event('input', { bubbles: true }));
                            return 'filled';
                        })();
                    """.trimIndent()
                    val result = evaluateJs(script)
                    results += "fill $selector: $result"
                }
                "navigate" -> {
                    action.target?.let { loadUrl(it) }
                    results += "navigated to ${action.target}"
                }
                "click" -> {
                    val target = action.target ?: continue
                    val result = if (target.startsWith("@e")) {
                        clickRef(target)
                    } else {
                        evaluateJs("""
                            (function() {
                                var el = document.querySelector('$target');
                                if (!el) return 'not_found';
                                el.click();
                                return 'clicked';
                            })();
                        """.trimIndent())
                    }
                    results += "click $target: $result"
                }
                "click_xy" -> {
                    val parts = action.target?.split(",") ?: continue
                    if (parts.size == 2) {
                        val x = parts[0].trim().toIntOrNull() ?: continue
                        val y = parts[1].trim().toIntOrNull() ?: continue
                        results += "click_xy: ${clickAt(x, y)}"
                    }
                }
                "scroll" -> {
                    val direction = action.value ?: "down"
                    results += "scroll: ${scroll(direction)}"
                }
            }
        }
        return results
    }

    private suspend fun evaluateJs(script: String, tabId: String? = null): String? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val webView = webViewFor(tabId)
                if (webView == null) {
                    cont.resume(null)
                    return@suspendCancellableCoroutine
                }
                webView.evaluateJavascript(script) { value ->
                    cont.resume(value?.trim('"')?.replace("\\n", "\n")?.replace("\\\"", "\""))
                }
            }
        }
}