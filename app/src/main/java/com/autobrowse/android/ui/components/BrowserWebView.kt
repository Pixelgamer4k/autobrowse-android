package com.autobrowse.android.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.viewinterop.AndroidView
import com.autobrowse.android.browser.AddressBarNavigation
import com.autobrowse.android.browser.BrowserController
import com.autobrowse.android.browser.ContentColorSampler
import com.autobrowse.android.browser.DesktopBrowserConfig
import com.autobrowse.android.browser.VirtualDisplayConfig
import com.autobrowse.android.domain.model.BrowserTab
import com.autobrowse.android.domain.model.BrowserTabStatus
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserWebView(
    tab: BrowserTab,
    controller: BrowserController,
    onTabUpdate: (BrowserTab) -> Unit,
    interactionEnabled: Boolean = true,
    sampleContentColor: Boolean = false,
    onContentColorSampled: (Color) -> Unit = {},
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

    fun reportContentColor() {
        if (!sampleContentColor) return
        ContentColorSampler.sampleTopBandAsync(webView) { sampled ->
            if (ContentColorSampler.isValid(sampled)) {
                onContentColorSampled(ContentColorSampler.dotsFromContent(ContentColorSampler.saturate(sampled)))
            }
        }
    }

    DisposableEffect(tab.id, onTabUpdate, sampleContentColor) {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                onTabUpdate(
                    tab.copy(
                        url = url ?: tab.url,
                        status = BrowserTabStatus.LOADING,
                    ),
                )
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                view?.post {
                    DesktopBrowserConfig.applyVirtualViewport(view)
                    reportContentColor()
                }
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
        webView.webChromeClient = WebChromeClient()
        onDispose { }
    }

    LaunchedEffect(tab.id, sampleContentColor) {
        if (!sampleContentColor) return@LaunchedEffect
        while (true) {
            delay(1800)
            reportContentColor()
        }
    }

    BoxWithConstraints(
        modifier = modifier.clipToBounds(),
    ) {
        val viewportWidthPx = maxWidth.value * density.density
        val viewportHeightPx = maxHeight.value * density.density
        val displayScale = VirtualDisplayConfig.scaleForViewport(viewportWidthPx, viewportHeightPx)

        LaunchedEffect(tab.id, tab.url) {
            if (tab.url.isBlank()) return@LaunchedEffect
            if (!AddressBarNavigation.urlsMatch(webView.url, tab.url)) {
                webView.loadUrl(tab.url)
            }
        }

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
                    layout(viewportWidthPx.roundToInt(), viewportHeightPx.roundToInt()) {
                        placeable.place(0, 0)
                    }
                },
            factory = { webView },
            update = { view ->
                if (view.isEnabled != interactionEnabled) {
                    view.isEnabled = interactionEnabled
                    view.isClickable = interactionEnabled
                }
            },
        )
    }
}