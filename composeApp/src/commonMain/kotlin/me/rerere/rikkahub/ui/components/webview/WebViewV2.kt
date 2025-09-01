package me.rerere.rikkahub.ui.components.webview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.multiplatform.webview.jsbridge.WebViewJsBridge
import com.multiplatform.webview.web.*

expect val platformWebviewParams: PlatformWebViewParams

@Composable
fun WebView(
    state: WebViewState,
    modifier: Modifier = Modifier,
    webViewJsBridge: WebViewJsBridge? = null,
    onCreated: (NativeWebView) -> Unit = {},
    webViewStateConfig: WebViewState.() -> Unit = {},
    factory: ((WebViewFactoryParam) -> NativeWebView)? = null,
) {
    LaunchedEffect(state) {
        state.apply {
            webSettings.apply {
                androidWebSettings.apply {
                    isJavaScriptEnabled = true // Enable JavaScript
                    domStorageEnabled = true
                    allowFileAccessFromFileURLs = true
                }
                iOSWebSettings.apply {

                }
            }
            webViewStateConfig()
        }
    }
    Box(
        modifier = modifier
    ) {
        WebView(
            state = state,
            modifier = modifier,
            webViewJsBridge = webViewJsBridge,
            onCreated = onCreated,
            platformWebViewParams = platformWebviewParams,
            factory = factory
        )
        if (state.isLoading) {
            LinearProgressIndicator(
                progress = { state.loadingProgress },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

val WebViewState.loadingProgress: Float
    get() = when (loadingState) {
        is LoadingState.Initializing -> 0f
        is LoadingState.Loading -> (loadingState as LoadingState.Loading).progress
        is LoadingState.Finished -> 1f
    }

expect fun NativeWebView.configureZoom()

expect fun NativeWebView.configureJsBridge(block: NativeWebView.() -> Unit)
