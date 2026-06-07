package com.autobrowse.android.browser

import android.webkit.WebView
import com.autobrowse.android.domain.model.AgentAction
import kotlinx.coroutines.suspendCancellableCoroutine
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

    private fun activeWebView(): WebView? = activeTabId?.let { webViews[it] }

    fun loadUrl(url: String, tabId: String? = null) {
        val normalized = AddressBarNavigation.resolve(url) ?: return
        val target = tabId?.let { webViews[it] } ?: activeWebView()
        target?.loadUrl(normalized)
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

    fun getCurrentUrl(tabId: String? = null): String? =
        (tabId?.let { webViews[it] } ?: activeWebView())?.url

    suspend fun evaluateScript(script: String, tabId: String? = null): String? =
        evaluateJs(script, tabId)

    suspend fun executeActions(actions: List<AgentAction>): List<String> {
        val results = mutableListOf<String>()
        for (action in actions) {
            when (action.type) {
                "fill" -> {
                    val selector = action.target ?: continue
                    val value = action.value ?: ""
                    val script = """
                        (function() {
                            var el = document.querySelector('$selector');
                            if (!el) return 'not_found';
                            el.value = '$value';
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
            }
        }
        return results
    }

    private suspend fun evaluateJs(script: String, tabId: String? = null): String? =
        suspendCancellableCoroutine { cont ->
            val webView = tabId?.let { webViews[it] } ?: activeWebView()
            if (webView == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            webView.evaluateJavascript(script) { value ->
                cont.resume(value?.trim('"'))
            }
        }
}