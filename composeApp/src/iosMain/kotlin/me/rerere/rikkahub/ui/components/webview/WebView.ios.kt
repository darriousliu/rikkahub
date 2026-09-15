package me.rerere.rikkahub.ui.components.webview

import dev.nucleusframework.webview.web.NativeWebView
import dev.nucleusframework.webview.web.WebViewState
import me.rerere.rikkahub.utils.JsonInstant
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.darwin.NSObject

internal actual fun configureWebViewState(state: WebViewState) = Unit

internal actual fun configureNativeWebView(
    view: NativeWebView,
    state: WebViewState,
    onConsoleMessage: (WebViewConsoleMessage) -> Unit,
) {
    view.configuration.userContentController.apply {
        addScriptMessageHandler(object : NSObject(), WKScriptMessageHandlerProtocol {
            override fun userContentController(userContentController: WKUserContentController, didReceiveScriptMessage: WKScriptMessage) {
                onConsoleMessage(JsonInstant.decodeFromString<WebViewConsoleMessage>(didReceiveScriptMessage.body as String))
            }
        }, "rikkaConsole")
        addUserScript(
            WKUserScript(
                source = webViewConsoleScript("window.webkit.messageHandlers.rikkaConsole.postMessage(JSON.stringify(entry));"),
                injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                forMainFrameOnly = false,
            ),
        )
    }
}

internal actual fun disposeWebView(view: NativeWebView) {
    view.configuration.userContentController.removeScriptMessageHandlerForName("rikkaConsole")
}
