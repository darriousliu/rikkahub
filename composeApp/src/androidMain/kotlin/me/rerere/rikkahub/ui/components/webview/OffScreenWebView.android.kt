@file:SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")

package me.rerere.rikkahub.ui.components.webview

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import me.rerere.common.PlatformContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

actual fun initGlobalWebView(): IWebView {
    val context = OffScreenWebViewInitHelper.get<PlatformContext>()
    val webView = WebView(context).apply {
        val deviceWidth = resources.displayMetrics.widthPixels
        val deviceHeight = resources.displayMetrics.heightPixels
        layoutParams = ViewGroup.LayoutParams(
            deviceWidth,  // Default width
            deviceHeight   // Default height
        )
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowContentAccess = true

        // Set up WebChromeClient and WebViewClient to update the shared state
        val state = OffScreenWebViewManager.webViewState
        webChromeClient = MyWebChromeClient(state)
        webViewClient = MyWebViewClient(state)

        // Force layout for off-screen rendering
        measure(
            View.MeasureSpec.makeMeasureSpec(deviceWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(deviceHeight, View.MeasureSpec.EXACTLY)
        )
        layout(0, 0, deviceWidth, deviceHeight)
    }
    return AndroidWebView(webView)
}

actual fun addJsBridgePlatform(name: String, bridge: Any) {
    val iWebView = OffScreenWebViewManager.webView ?: return
    (iWebView.webView).addJavascriptInterface(bridge, name)
}

private object OffScreenWebViewInitHelper : KoinComponent
