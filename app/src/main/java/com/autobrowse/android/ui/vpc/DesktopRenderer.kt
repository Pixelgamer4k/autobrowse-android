package com.autobrowse.android.ui.vpc

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DesktopRenderer(
    demoMode: Boolean,
    websockifyPort: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val assetLoader = remember {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }
    val pagePath = if (demoMode) {
        "https://appassets.androidplatform.net/assets/vpc/demo-desktop.html"
    } else {
        "https://appassets.androidplatform.net/assets/vpc/novnc/vnc.html"
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                setBackgroundColor(android.graphics.Color.BLACK)
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest,
                    ) = assetLoader.shouldInterceptRequest(request.url)
                }
                loadUrl(pagePath)
            }
        },
        update = { view ->
            if (view.url != pagePath) view.loadUrl(pagePath)
        },
    )
}