@file:OptIn(ExperimentalNativeApi::class)

package me.rerere.rikkahub.ui.components.webview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.cValue
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSOperatingSystemVersion
import platform.Foundation.NSProcessInfo
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
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
            modifier = Modifier.fillMaxWidth(),
            update = {
                state.webView = IOSWebView(it)
                onUpdated(state.webView!!.webView)
            },
            onRelease = {
                state.webView = null
            },
            onReset = {
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
