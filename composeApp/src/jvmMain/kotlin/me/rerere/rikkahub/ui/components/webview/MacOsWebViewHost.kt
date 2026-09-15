@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.rerere.rikkahub.ui.components.webview

import dev.nucleusframework.window.tao.TaoNativeViewHost
import dev.nucleusframework.window.tao.ffi.NativeTaoMacOsNativeViewBridge
import dev.nucleusframework.window.tao.scene.TaoComposeSceneHost
import java.lang.reflect.Field

/**
 * Nucleus 2.5.15 queues frame/radius updates with raw NSView pointers, but composewebview 1.0.3 releases
 * those views immediately. Keep the same Metal interop transaction and skip actions from detached mounts.
 * All calls, including queued actions, run on the macOS main thread.
 */
internal class MacOsWebViewHost(
    private val delegate: TaoNativeViewHost,
    private val enqueue: (() -> Unit) -> Unit,
    private val setNativeFrame: (Long, Int, Int, Int, Int) -> Unit,
    private val setNativeCornerRadius: (Long, Float) -> Unit,
) : TaoNativeViewHost by delegate {
    private class Mount(val handle: Long)

    private val mounts = mutableMapOf<Any, Mount>()

    override fun attach(childHandle: Long, regionToken: Any) {
        delegate.attach(childHandle, regionToken)
        mounts[regionToken] = Mount(childHandle)
    }

    override fun detach(childHandle: Long, regionToken: Any) {
        if (mounts[regionToken]?.handle != childHandle) return
        mounts.remove(regionToken)
        delegate.detach(childHandle, regionToken)
    }

    fun beforeDestroy(childHandle: Long) {
        mounts.filterValues { it.handle == childHandle }.keys.toList().forEach { token ->
            detach(childHandle, token)
        }
    }

    override fun setFrame(handle: Long, xPx: Int, yPx: Int, widthPx: Int, heightPx: Int, regionToken: Any) {
        val mount = mounts[regionToken]?.takeIf { it.handle == handle } ?: return
        enqueue {
            if (mounts[regionToken] === mount) {
                setNativeFrame(handle, xPx, yPx, widthPx, heightPx)
            }
        }
    }

    override fun setCornerRadius(handle: Long, radiusPx: Float) {
        val (token, mount) = mounts.entries.firstOrNull { it.value.handle == handle } ?: return
        enqueue {
            if (mounts[token] === mount) {
                setNativeCornerRadius(handle, radiusPx)
            }
        }
    }
}

internal fun createMacOsWebViewHost(host: TaoNativeViewHost, parentNsView: Long): MacOsWebViewHost {
    // Nucleus exposes its transaction scheduler only on the captured scene host. This version-specific
    // adapter is covered by an ABI test and a ProGuard keep rule; review it when upgrading Nucleus.
    val sceneHost = macOsSceneHostField(host.javaClass).get(host) as TaoComposeSceneHost
    return MacOsWebViewHost(
        delegate = host,
        enqueue = { action -> sceneHost.scheduleInteropAction(action) },
        setNativeFrame = { handle, x, y, width, height ->
            NativeTaoMacOsNativeViewBridge.nativeSetSubviewFrame(parentNsView, handle, x, y, width, height)
        },
        setNativeCornerRadius = { handle, radius ->
            NativeTaoMacOsNativeViewBridge.nativeSetSubviewCornerRadius(parentNsView, handle, radius)
        },
    )
}

internal fun macOsSceneHostField(hostClass: Class<*>): Field =
    hostClass.declaredFields.single { it.type == TaoComposeSceneHost::class.java }.apply { isAccessible = true }
