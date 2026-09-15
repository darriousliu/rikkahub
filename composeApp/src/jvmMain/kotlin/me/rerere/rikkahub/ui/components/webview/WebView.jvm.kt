@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.rerere.rikkahub.ui.components.webview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import dev.nucleusframework.webview.web.NativeWebView
import dev.nucleusframework.webview.web.WebViewState
import dev.nucleusframework.webview.web.macos.MacOsWebKitNativeWebView
import dev.nucleusframework.window.tao.LocalTaoNativeViewHost
import dev.nucleusframework.window.tao.LocalTaoWindow
import dev.nucleusframework.window.tao.TaoNativeViewHost
import dev.nucleusframework.window.tao.ffi.NativeTaoMacOsNativeViewBridge
import dev.nucleusframework.window.tao.scene.TaoComposeSceneHost
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.filesDir
import me.rerere.rikkahub.shared.currentPlatformKind
import me.rerere.rikkahub.shared.isMacOS
import me.rerere.rikkahub.shared.isWindows
import java.io.File
import java.lang.reflect.Field

internal actual fun configureWebViewState(state: WebViewState) {
    if (currentPlatformKind.isWindows) {
        configureWindowsWebViewDataDirectory(state, FileKit.filesDir.file)
    }
    state.webSettings.desktopWebSettings.initScript = webViewConsoleScript(
        "window.ipc.postMessage(JSON.stringify({methodName:'console',params:JSON.stringify(entry),callbackId:-1}));",
    )
}

internal actual fun configureNativeWebView(
    view: NativeWebView,
    state: WebViewState,
    onConsoleMessage: (WebViewConsoleMessage) -> Unit,
) = Unit

internal actual fun disposeWebView(view: NativeWebView) = Unit

internal fun configureWindowsWebViewDataDirectory(state: WebViewState, applicationDirectory: File) {
    val settings = state.webSettings.desktopWebSettings
    if (settings.dataDirectory.isNullOrBlank() && !settings.incognito) {
        // WebView2 otherwise writes beside java.exe (often a read-only JBR directory in jvmHotRun).
        settings.dataDirectory = File(applicationDirectory, "webview2").absolutePath
    }
}

@Composable
internal actual fun WebViewLifecycle(content: @Composable ((NativeWebView) -> Unit) -> Unit) {
    val host = LocalTaoNativeViewHost.current
    val window = LocalTaoWindow.current
    if (!currentPlatformKind.isMacOS || host == null || window == null) {
        content(::disposeWebView)
        return
    }
    val guardedHost = remember(host, window) { createWebViewLifecycleHost(host, window.nativeHandle) }
    CompositionLocalProvider(LocalTaoNativeViewHost provides guardedHost) {
        content { view ->
            // The library calls this immediately before nativeWebView.destroy(). Detach while the NSView is live,
            // and invalidate queued layout actions even when NativeView's own onDispose has not run yet.
            if (view is MacOsWebKitNativeWebView) {
                guardedHost.beforeDestroy(view.asPlatformView().nsViewHandle)
            }
            disposeWebView(view)
        }
    }
}

private fun createWebViewLifecycleHost(host: TaoNativeViewHost, parentViewHandle: Long): WebViewLifecycleHost {
    // Nucleus 2.5.15's macOS interop queues raw pointers, while composewebview 1.0.3 releases views immediately.
    // Its transaction scheduler lives on the captured scene host. This adapter has an ABI test and a ProGuard
    // keep rule; review it when upgrading Nucleus.
    val sceneHost = findTaoSceneHostField(host.javaClass).get(host) as TaoComposeSceneHost
    return WebViewLifecycleHost(
        delegate = host,
        enqueue = { action -> sceneHost.scheduleInteropAction(action) },
        setNativeFrame = { handle, x, y, width, height ->
            NativeTaoMacOsNativeViewBridge.nativeSetSubviewFrame(parentViewHandle, handle, x, y, width, height)
        },
        setNativeCornerRadius = { handle, radius ->
            NativeTaoMacOsNativeViewBridge.nativeSetSubviewCornerRadius(parentViewHandle, handle, radius)
        },
    )
}

internal fun findTaoSceneHostField(hostClass: Class<*>): Field =
    hostClass.declaredFields.single { it.type == TaoComposeSceneHost::class.java }.apply { isAccessible = true }
