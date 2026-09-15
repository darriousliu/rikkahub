@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.rerere.rikkahub.ui.components.webview

import dev.nucleusframework.window.tao.TaoNativeViewHost

/**
 * Tracks native view mounts so queued frame/radius updates cannot outlive the view they target.
 * Updates stay in the host's render transaction; all calls run on the host's UI thread.
 */
internal class WebViewLifecycleHost(
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
