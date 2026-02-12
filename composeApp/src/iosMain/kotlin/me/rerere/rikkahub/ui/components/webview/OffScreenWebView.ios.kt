@file:OptIn(ExperimentalNativeApi::class)
@file:Suppress("UNCHECKED_CAST")

package me.rerere.rikkahub.ui.components.webview

import co.touchlab.kermit.Logger
import kotlinx.cinterop.cValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSDictionary
import platform.Foundation.NSNumber
import platform.Foundation.NSOperatingSystemVersion
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIScreen
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject
import kotlin.experimental.ExperimentalNativeApi

private const val TAG = "OffScreenWebView"

// Keep a reference to the WKUserContentController so we can add handlers later
private var globalUserContentController: WKUserContentController? = null

actual fun initGlobalWebView(): IWebView {
    val config = WKWebViewConfiguration().apply {
        allowsInlineMediaPlayback = true
        defaultWebpagePreferences.allowsContentJavaScript = true
    }

    val userContentController = WKUserContentController()

    // Inject console log bridge
    configureConsoleLog(userContentController) { message, level, sourceId, lineNumber ->
        Logger.d(TAG) { "Console [$level] $message (Source: $sourceId, Line: $lineNumber)" }
        OffScreenWebViewManager.webViewState.pushConsoleMessage(
            ConsoleMessage(
                message = message,
                messageLevelName = level,
                sourceId = sourceId,
                lineNumber = lineNumber,
            )
        )
    }

    config.userContentController = userContentController
    globalUserContentController = userContentController
    val (deviceWidth, deviceHeight) = UIScreen.mainScreen.bounds.useContents {
        size.width to size.height
    }

    val webView = WKWebView(
        frame = CGRectMake(0.0, 0.0, deviceWidth, deviceHeight), // Default iPhone size
        configuration = config,
    ).apply {
        val minSetInspectableVersion = cValue<NSOperatingSystemVersion> {
            majorVersion = 16
            minorVersion = 4
            patchVersion = 0
        }
        if (NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(minSetInspectableVersion)) {
            this.setInspectable(Platform.isDebugBinary)
        }
    }
    webView.setNeedsLayout()
    webView.layoutIfNeeded()

    return IOSWebView(webView)
}

actual fun addJsBridgePlatform(name: String, bridge: Any) {
    val controller = globalUserContentController ?: return
    val handler = (bridge as? (String) -> Unit) ?: run {
        Logger.e(TAG) { "addJsBridgePlatform: bridge must be a (String) -> Unit lambda on iOS" }
        return
    }

    // Inject JS function that posts message to native handler
    val jsCode = """
        window.$name = function(data) {
            window.webkit.messageHandlers.$name.postMessage(data);
        };
    """.trimIndent()

    val userScript = WKUserScript(
        source = jsCode,
        injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
        forMainFrameOnly = true,
    )
    controller.addUserScript(userScript)

    val messageHandler = object : NSObject(), WKScriptMessageHandlerProtocol {
        override fun userContentController(
            userContentController: WKUserContentController,
            didReceiveScriptMessage: WKScriptMessage,
        ) {
            Logger.i(TAG) { "Received message from JS: ${didReceiveScriptMessage.body}" }
            val message = didReceiveScriptMessage.body.toString()
            handler(message)
        }
    }

    controller.addScriptMessageHandler(
        scriptMessageHandler = messageHandler,
        name = name,
    )

    // Also evaluate the JS immediately in case a page is already loaded
    OffScreenWebViewManager.webView?.evaluateJavascript(jsCode, null)
}

private fun configureConsoleLog(
    userContentController: WKUserContentController,
    onConsoleMessage: (String, String, String, Int) -> Unit,
) {
    val consoleScript = """
        (function() {
            var console = window.console;
            var logLevels = ['log', 'info', 'warn', 'error', 'debug'];

            logLevels.forEach(function(level) {
                var originalMethod = console[level];
                console[level] = function() {
                    var message = Array.prototype.slice.call(arguments).map(function(arg) {
                        return typeof arg === 'object' ? JSON.stringify(arg) : String(arg);
                    }).join(' ');

                    window.webkit.messageHandlers.consoleHandler.postMessage({
                        level: level,
                        message: message,
                        sourceId: window.location.href,
                        lineNumber: 0
                    });

                    originalMethod.apply(console, arguments);
                };
            });
        })();
    """.trimIndent()

    val consoleUserScript = WKUserScript(
        source = consoleScript,
        injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
        forMainFrameOnly = false,
    )
    userContentController.addUserScript(consoleUserScript)

    val messageHandler = object : NSObject(), WKScriptMessageHandlerProtocol {
        override fun userContentController(
            userContentController: WKUserContentController,
            didReceiveScriptMessage: WKScriptMessage,
        ) {
            val body = didReceiveScriptMessage.body as? NSDictionary
            if (body != null) {
                val level = body.objectForKey("level") as? String ?: "log"
                val message = body.objectForKey("message") as? String ?: ""
                val sourceId = body.objectForKey("sourceId") as? String ?: ""
                val lineNumber = (body.objectForKey("lineNumber") as? NSNumber)?.intValue ?: 0

                onConsoleMessage(message, level, sourceId, lineNumber)
            }
        }
    }
    userContentController.addScriptMessageHandler(
        scriptMessageHandler = messageHandler,
        name = "consoleHandler",
    )
}
