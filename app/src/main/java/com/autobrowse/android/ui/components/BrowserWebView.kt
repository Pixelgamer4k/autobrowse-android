package com.autobrowse.android.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.autobrowse.android.browser.BrowserController
import com.autobrowse.android.browser.DesktopBrowserConfig
import com.autobrowse.android.domain.model.BrowserTab
import com.autobrowse.android.domain.model.BrowserTabStatus

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserWebView(
    tab: BrowserTab,
    controller: BrowserController,
    onTabUpdate: (BrowserTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val webView = remember(tab.id) {
        WebView(context.applicationContext).apply {
            DesktopBrowserConfig.apply(this)
        }
    }

    DisposableEffect(tab.id) {
        controller.attach(tab.id, webView)
        onDispose { controller.detach(tab.id) }
    }

    AndroidView(
        modifier = modifier,
        factory = { webView },
        update = { view ->
            DesktopBrowserConfig.apply(view)
            view.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    onTabUpdate(tab.copy(url = url ?: tab.url, status = BrowserTabStatus.LOADING))
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    view?.evaluateJavascript(DesktopBrowserConfig.VIEWPORT_INJECT_SCRIPT, null)
                    onTabUpdate(
                        tab.copy(
                            url = url ?: tab.url,
                            title = view?.title ?: tab.title,
                            status = if (tab.isAgentControlled) {
                                BrowserTabStatus.AGENT_CONTROLLED
                            } else {
                                BrowserTabStatus.ACTIVE
                            },
                        ),
                    )
                }

                @Deprecated("Deprecated in Java")
                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?,
                ) {
                    onTabUpdate(tab.copy(status = BrowserTabStatus.ERROR))
                }
            }
            view.webChromeClient = WebChromeClient()
            if (view.url != tab.url) {
                view.loadUrl(tab.url)
            }
        },
    )
}