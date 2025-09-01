@file:OptIn(ExperimentalNativeApi::class)

package me.rerere.rikkahub.ui.components.webview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import co.touchlab.kermit.Logger
import kotlinx.cinterop.cValue
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSOperatingSystemVersion
import platform.Foundation.NSProcessInfo
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject
import kotlin.experimental.ExperimentalNativeApi

actual typealias NativeWebView = WKWebView

actual abstract class WebSettings(val settings: WKWebViewConfiguration)

private class WebSettingsImpl(settings: WKWebViewConfiguration) : WebSettings(settings)

actual class ConsoleMessage

actual fun WebSettings.configureZoom() {
    // iOS WKWebView has built-in zoom controls; no additional configuration needed
}

@Composable
actual fun WebView(
    state: WebViewState,
    modifier: Modifier,
    onCreated: (NativeWebView) -> Unit,
    onUpdated: (NativeWebView) -> Unit
) {
    Box(
        modifier = modifier
    ) {
        UIKitView(
            factory = {
                val config =
                    WKWebViewConfiguration().apply {
                        allowsInlineMediaPlayback = true
                        defaultWebpagePreferences.allowsContentJavaScript = state.javaScriptEnabled
//                    preferences.apply {
//                        setValue(
//                            state.webSettings.allowFileAccessFromFileURLs,
//                            forKey = "allowFileAccessFromFileURLs",
//                        )
//                        javaScriptEnabled = state.javaScriptEnabled
//                    }
//                    setValue(
//                        value = state.webSettings.allowUniversalAccessFromFileURLs,
//                        forKey = "allowUniversalAccessFromFileURLs",
//                    )
                    }
                val userContentController = WKUserContentController()
                // 注入 JavaScript Bridge
                state.iosInterfaces.forEach { (functionName, handler) ->
                    val jsCode = """
                    window.$functionName = function(data) {
                        window.webkit.messageHandlers.$functionName.postMessage(data);
                    };
                """.trimIndent()

                    val userScript = WKUserScript(
                        source = jsCode,
                        injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                        forMainFrameOnly = true
                    )
                    userContentController.addUserScript(userScript)

                    // 创建消息处理器
                    val messageHandler = object : NSObject(), WKScriptMessageHandlerProtocol {
                        override fun userContentController(
                            userContentController: WKUserContentController,
                            didReceiveScriptMessage: WKScriptMessage
                        ) {
                            Logger.i("WebView") { "Received message from JS: ${didReceiveScriptMessage.body}, type: ${didReceiveScriptMessage.body::class.qualifiedName}" }
                            val message = didReceiveScriptMessage.body.toString()
                            handler(message)
                        }
                    }

                    userContentController.addScriptMessageHandler(
                        scriptMessageHandler = messageHandler,
                        name = functionName
                    )
                }
                config.userContentController = userContentController
                WKWebView(
                    frame = CGRectZero.readValue(),
                    configuration = config.apply {
                        state.settings(WebSettingsImpl(this))
                    },
                ).apply {
                    onCreated(this)
//                    allowsBackForwardNavigationGestures = captureBackPresses
//                    customUserAgent = state.webSettings.customUserAgentString
//                    this.addProgressObservers(
//                        observer = observer,
//                    )
//                    this.navigationDelegate = navigationDelegate

//                    state.webSettings.let {
//                        val backgroundColor =
//                            (it.iOSWebSettings.backgroundColor ?: it.backgroundColor).toUIColor()
//                        val scrollViewColor =
//                            (
//                                it.iOSWebSettings.underPageBackgroundColor
//                                    ?: it.backgroundColor
//                                ).toUIColor()
//                        setOpaque(it.iOSWebSettings.opaque)
//                        if (!it.iOSWebSettings.opaque) {
//                            setBackgroundColor(backgroundColor)
//                            scrollView.setBackgroundColor(scrollViewColor)
//                        }
//                        scrollView.pinchGestureRecognizer?.enabled = it.supportZoom
//                    }
//                    state.webSettings.iOSWebSettings.let {
//                        with(scrollView) {
//                            bounces = it.bounces
//                            scrollEnabled = it.scrollEnabled
//                            showsHorizontalScrollIndicator = it.showHorizontalScrollIndicator
//                            showsVerticalScrollIndicator = it.showVerticalScrollIndicator
//                        }
//                    }

                    /**
                     * Sets the inspectable property of the WKWebView.
                     * This is only done if the operating system version is iOS 16.4 or later
                     * to prevent crashes on lower versions where the `setInspectable` method is not available.
                     * Enabling this allows Safari Web Inspector to debug the content of the WebView.
                     * The value is determined by `state.webSettings.iOSWebSettings.isInspectable`.
                     */
                    val minSetInspectableVersion =
                        cValue<NSOperatingSystemVersion> {
                            majorVersion = 16
                            minorVersion = 4
                            patchVersion = 0
                        }
                    if (NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(minSetInspectableVersion)) {
                        this.setInspectable(Platform.isDebugBinary)
                    }
                }.also {
                    val iosWebView = IOSWebView(it)
                    state.webView = iosWebView
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { webView ->
                state.webView = IOSWebView(webView)
                Logger.i("WebView") { "UIKitView: Updating WebView" }

                when (val content = state.content) {
                    is WebContent.Url -> {
                        val url = content.url
                        // Only load new URL if it's different from the current one or if the state forces reload
                        // Also check if the webView's url is null or blank, which might happen initially
                        val currentWebViewUrl = webView.URL?.absoluteString
                        if (url.isNotEmpty() && (currentWebViewUrl.isNullOrBlank() || url != currentWebViewUrl || state.forceReload)) {
                            state.webView?.loadUrl(content.url, content.additionalHttpHeaders)
                            state.forceReload = false // Reset force reload flag
                        }
                    }

                    is WebContent.Data -> {
                        // Check if the data needs to be reloaded (e.g., if different from last loaded data)
                        // For simplicity, we might just reload it every time the update block runs with Data content.
                        // A more complex check could involve comparing `content.data` with a previously stored value.
                        state.webView?.loadData(
                            content.data,
                            content.baseUrl,
                            content.encoding,
                            content.mimeType,
                            content.historyUrl
                        )
                        // Assuming data loading is fast, but let's reflect the state more accurately
                        // state.isLoading = false // This might be too soon, let WebViewClient handle it
                    }

                    WebContent.NavigatorOnly -> {
                        // NO-OP: State changes related to navigation are handled by the methods in WebViewState
                    }
                }
                onUpdated(webView)
            },
            onRelease = {
                state.webView = null
            },
            onReset = {
                Logger.i("WebView") { "UIKitView: Resetting WebView" }
            },
            properties =
                UIKitInteropProperties(
                    isNativeAccessibilityEnabled = true,
                ),
        )
        // Loading Progress Indicator
        if (state.isLoading) {
            LinearProgressIndicator(
                progress = { state.loadingProgress },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

actual fun ConsoleMessage.messageLevelName(): String {
    TODO("Not yet implemented")
}

actual fun ConsoleMessage.message(): String {
    TODO("Not yet implemented")
}

actual fun ConsoleMessage.sourceId(): String {
    TODO("Not yet implemented")
}

actual fun ConsoleMessage.lineNumber(): Int {
    TODO("Not yet implemented")
}
