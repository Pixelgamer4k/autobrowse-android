package com.autobrowse.android.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.viewinterop.AndroidView
import com.autobrowse.android.browser.BrowserController
import com.autobrowse.android.browser.DesktopBrowserConfig
import com.autobrowse.android.browser.VirtualDisplayConfig
import com.autobrowse.android.domain.model.BrowserTab
import com.autobrowse.android.domain.model.BrowserTabStatus
import kotlin.math.roundToInt

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserWebView(
    tab: BrowserTab,
    controller: BrowserController,
    onTabUpdate: (BrowserTab) -> Unit,
    interactionEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val webView = remember(tab.id) {
        WebView(context.applicationContext).apply {
            DesktopBrowserConfig.apply(this)
            layoutParams = ViewGroup.LayoutParams(
                VirtualDisplayConfig.WIDTH,
                VirtualDisplayConfig.HEIGHT,
            )
        }
    }

    DisposableEffect(tab.id) {
        controller.attach(tab.id, webView)
        onDispose { controller.detach(tab.id) }
    }

    BoxWithConstraints(
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.TopStart,
    ) {
        val viewportWidthPx = maxWidth.value * density.density
        val displayScale = VirtualDisplayConfig.scaleForViewport(viewportWidthPx)
        val scaledWidthPx = (VirtualDisplayConfig.WIDTH * displayScale).roundToInt()
        val scaledHeightPx = (VirtualDisplayConfig.HEIGHT * displayScale).roundToInt()

        AndroidView(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = displayScale
                    scaleY = displayScale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .layout { measurable, _ ->
                    val placeable = measurable.measure(
                        Constraints.fixed(
                            VirtualDisplayConfig.WIDTH,
                            VirtualDisplayConfig.HEIGHT,
                        ),
                    )
                    layout(scaledWidthPx, scaledHeightPx) {
                        placeable.place(0, 0)
                    }
                },
            factory = { webView },
            update = { view ->
                view.isEnabled = interactionEnabled
                view.isClickable = interactionEnabled
                view.layoutParams = ViewGroup.LayoutParams(
                    VirtualDisplayConfig.WIDTH,
                    VirtualDisplayConfig.HEIGHT,
                )
                DesktopBrowserConfig.apply(view)
                view.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        onTabUpdate(
                            tab.copy(
                                url = url ?: tab.url,
                                status = BrowserTabStatus.LOADING,
                            ),
                        )
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.post { DesktopBrowserConfig.applyVirtualViewport(view) }
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
}