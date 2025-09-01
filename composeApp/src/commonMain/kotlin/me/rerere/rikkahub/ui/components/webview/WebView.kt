package me.rerere.rikkahub.ui.components.webview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

expect class NativeWebView

expect abstract class WebSettings

expect class ConsoleMessage

expect fun WebSettings.configureZoom()

@Composable
expect fun WebView(
    state: WebViewState,
    modifier: Modifier = Modifier,
    onCreated: (NativeWebView) -> Unit = {},
    onUpdated: (NativeWebView) -> Unit = {},
)

// --- State and Content Definition ---
sealed class WebContent {
    data class Url(
        val url: String,
        val additionalHttpHeaders: Map<String, String> = emptyMap(),
        val clearHistory: Boolean = false
    ) : WebContent()

    data class Data(
        val data: String,
        val baseUrl: String? = null,
        val encoding: String = "utf-8",
        val mimeType: String? = null,
        val historyUrl: String? = null
    ) : WebContent()

    data object NavigatorOnly : WebContent()
}

@Stable // Mark as Stable for better Compose performance
class WebViewState(
    initialContent: WebContent = WebContent.NavigatorOnly,
    val androidInterfaces: Map<String, Any> = emptyMap(),
    val iosInterfaces: Map<String, (String) -> Unit> = emptyMap(),
    val settings: WebSettings.() -> Unit = {}
) {
    // --- Content State ---
    var content: WebContent by mutableStateOf(initialContent)
    internal var forceReload: Boolean by mutableStateOf(false) // Internal state to force URL reload if needed

    // --- Loading State ---
    var isLoading: Boolean by mutableStateOf(false)
        internal set // Only WebViewClients should modify this
    var loadingProgress: Float by mutableFloatStateOf(0f)
        internal set

    // --- Page Information ---
    var pageTitle: String? by mutableStateOf(null)
        internal set
    var currentUrl: String? by mutableStateOf(null)
        internal set

    // --- Navigation State ---
    var canGoBack: Boolean by mutableStateOf(false)
        internal set
    var canGoForward: Boolean by mutableStateOf(false)
        internal set

    // --- Console Message ---
    var consoleMessages: List<ConsoleMessage> by mutableStateOf(emptyList())
        internal set

    // --- Settings ---
    var javaScriptEnabled: Boolean by mutableStateOf(true) // Example setting

    // --- WebView Instance ---
    // Hold the WebView instance internally to perform actions.
    // Be cautious with this reference, ensure it doesn't leak context.
    internal var webView: IWebView? = null

    // --- Public Actions ---

    fun loadUrl(
        url: String,
        additionalHttpHeaders: Map<String, String> = emptyMap()
    ) {
        // Determine if reload is needed: same URL or explicit force flag set elsewhere
        forceReload =
            (content is WebContent.Url && (content as WebContent.Url).url == url) || forceReload
        content = WebContent.Url(url, additionalHttpHeaders)
    }

    fun loadData(
        data: String,
        baseUrl: String? = null,
        encoding: String = "utf-8",
        mimeType: String? = null,
        historyUrl: String? = null
    ) {
        content = WebContent.Data(data, baseUrl, encoding, mimeType, historyUrl)
    }

    // --- Navigation Methods ---
    fun goBack() {
        webView?.goBack()
    }

    fun goForward() {
        webView?.goForward()
    }

    fun reload() {
        // Set forceReload flag for URL content type to ensure `update` block reloads
        forceReload = true
        // Trigger recomposition/update by changing the content reference slightly,
        // even if the URL is the same. Assigning the same Url object might not trigger update.
        // Or simply call webView?.reload() directly.
        webView?.reload()
        // If content is Data, reloading might mean re-setting the data.
        if (content is WebContent.Data) {
            // Re-assign to trigger update block if necessary
            content = (content as WebContent.Data).copy()
        }
    }

    fun stopLoading() {
        webView?.stopLoading()
    }

    fun clearHistory() {
        webView?.clearHistory()
    }

    fun pushConsoleMessage(message: ConsoleMessage) {
        consoleMessages = consoleMessages + message
        if (consoleMessages.size > 64) { // Limit to 64 messages
            consoleMessages = consoleMessages.takeLast(64)
        }
    }
}

@Composable
fun rememberWebViewState(
    url: String = "about:blank",
    additionalHttpHeaders: Map<String, String> = emptyMap(),
    androidInterfaces: Map<String, Any> = emptyMap(),
    iosInterfaces: Map<String, (String) -> Unit> = emptyMap(),
    settings: WebSettings.() -> Unit = {},
) = remember(url, additionalHttpHeaders) { // Use keys for better recomposition control
    WebViewState(
        initialContent = WebContent.Url(url, additionalHttpHeaders),
        androidInterfaces = androidInterfaces,
        iosInterfaces = iosInterfaces,
        settings = settings
    )
}

@Composable
fun rememberWebViewState(
    data: String,
    baseUrl: String? = null,
    encoding: String = "utf-8",
    mimeType: String? = null,
    historyUrl: String? = null,
    androidInterfaces: Map<String, Any> = emptyMap(),
    iosInterfaces: Map<String, (String) -> Unit> = emptyMap(),
    settings: WebSettings.() -> Unit = {},
) = remember(data, baseUrl, encoding, mimeType, historyUrl) { // Use keys
    WebViewState(
        initialContent = WebContent.Data(data, baseUrl, encoding, mimeType, historyUrl),
        androidInterfaces = androidInterfaces,
        iosInterfaces = iosInterfaces,
        settings = settings
    )
}

expect fun ConsoleMessage.messageLevelName(): String

expect fun ConsoleMessage.message(): String

expect fun ConsoleMessage.sourceId(): String

expect fun ConsoleMessage.lineNumber(): Int
