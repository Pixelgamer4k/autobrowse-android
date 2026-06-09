package com.autobrowse.android.browser

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.PixelCopy
import android.view.View
import android.view.Window
import android.webkit.WebView
import com.autobrowse.android.domain.model.AgentAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class BrowserController(
    private val onDownloadRequested: ((
        tabId: String,
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
    ) -> Unit)? = null,
) {
    private val webViews = ConcurrentHashMap<String, WebView>()
    private var activeTabId: String? = null

    fun attach(tabId: String, webView: WebView) {
        webViews[tabId] = webView
        onDownloadRequested?.let { handler ->
            webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                handler(tabId, url, userAgent, contentDisposition, mimeType, contentLength)
            }
        }
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

    fun goForward(tabId: String? = null) {
        webViewFor(tabId)?.goForward()
    }

    fun reload(tabId: String? = null) {
        webViewFor(tabId)?.reload()
    }

    fun stopLoading(tabId: String? = null) {
        webViewFor(tabId)?.stopLoading()
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

    suspend fun waitForPageReady(tabId: String? = null, extraDelayMs: Long = 2000L): String {
        if (webViewFor(tabId) == null) return "no_webview"
        var ready = false
        for (i in 0 until 24) {
            val state = evaluateJs("document.readyState", tabId)
            if (state == "complete") {
                ready = true
                break
            }
            delay(200)
        }
        delay(extraDelayMs.coerceIn(300, 5000))
        return evaluateJs(
            """
            (function() {
                if (document.readyState !== 'complete') return 'loading';
                var body = document.body ? document.body.innerText.length : 0;
                return body > 20 ? 'ready' : 'ready_sparse';
            })();
            """.trimIndent(),
            tabId,
        ) ?: if (ready) "ready" else "timeout"
    }

    suspend fun captureScreenshotBase64(tabId: String? = null): String? = withContext(Dispatchers.Main) {
        val webView = webViewFor(tabId) ?: return@withContext null
        ensureWebViewLaidOut(webView)
        awaitWebViewDrawn(webView)
        val width = webView.width.coerceIn(1, 1200)
        val height = webView.height.coerceIn(1, 2000)
        val bitmap = captureWebViewBitmap(webView, width, height) ?: return@withContext null
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        bitmap.recycle()
        Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun ensureWebViewLaidOut(webView: WebView) {
        if (webView.width > 0 && webView.height > 0) return
        val w = VirtualDisplayConfig.WIDTH.coerceAtLeast(1)
        val h = VirtualDisplayConfig.HEIGHT.coerceAtLeast(1)
        webView.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
        )
        webView.layout(0, 0, w, h)
    }

    private suspend fun awaitWebViewDrawn(webView: WebView) = suspendCancellableCoroutine { cont ->
        webView.post {
            webView.post {
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    private suspend fun captureWebViewBitmap(webView: WebView, width: Int, height: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            captureWithWindowPixelCopy(webView, width, height)?.let { return it }
        }
        return drawWebViewSoftware(webView, width, height)
    }

    private suspend fun captureWithWindowPixelCopy(
        webView: WebView,
        width: Int,
        height: Int,
    ): Bitmap? {
        val window = findWindow(webView) ?: return null
        if (webView.width <= 0 || webView.height <= 0) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val location = IntArray(2)
        webView.getLocationInWindow(location)
        val srcRect = Rect(
            location[0],
            location[1],
            location[0] + webView.width,
            location[1] + webView.height,
        )
        val ok = suspendCancellableCoroutine { cont: kotlinx.coroutines.CancellableContinuation<Boolean> ->
            PixelCopy.request(
                window,
                srcRect,
                bitmap,
                PixelCopy.OnPixelCopyFinishedListener { result ->
                    cont.resume(result == PixelCopy.SUCCESS)
                },
                Handler(Looper.getMainLooper()),
            )
        }
        return if (ok) bitmap else {
            bitmap.recycle()
            null
        }
    }

    private fun findWindow(webView: WebView): Window? {
        findActivity(webView.rootView?.context ?: webView.context)?.let { return it.window }
        return findActivity(webView.context)?.window
    }

    private fun findActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private fun drawWebViewSoftware(webView: WebView, width: Int, height: Int): Bitmap? {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val previousLayer = webView.layerType
        try {
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            webView.draw(Canvas(bitmap))
            return bitmap
        } catch (_: Exception) {
            bitmap.recycle()
            return null
        } finally {
            webView.setLayerType(previousLayer, null)
        }
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