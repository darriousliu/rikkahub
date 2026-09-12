package me.rerere.rikkahub.ui.components.webview

import io.github.kdroidfilter.webview.web.NativeWebView
import io.github.kdroidfilter.webview.web.WebViewFactoryParam
import io.github.kdroidfilter.webview.web.defaultWebViewFactory
import me.rerere.rikkahub.utils.JsonInstant
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.darwin.NSObject

internal actual fun createWebView(
    param: WebViewFactoryParam,
    onConsoleMessage: (WebViewConsoleMessage) -> Unit,
): NativeWebView {
    param.config.userContentController.apply {
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
    return defaultWebViewFactory(param)
}

internal actual fun disposeWebView(view: NativeWebView) {
    view.configuration.userContentController.removeScriptMessageHandlerForName("rikkaConsole")
}
