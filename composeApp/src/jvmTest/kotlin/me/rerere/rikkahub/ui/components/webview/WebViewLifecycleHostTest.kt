@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.rerere.rikkahub.ui.components.webview

import dev.nucleusframework.window.tao.TaoNativeViewHost
import dev.nucleusframework.window.tao.scene.TaoComposeSceneHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebViewLifecycleHostTest {
    @Test
    fun mountedViewKeepsLayoutInTheRenderTransaction() {
        val fixture = Fixture()
        val token = Any()
        fixture.attach(7L, token)
        fixture.host.setFrame(7L, 10, 20, 640, 200, token)
        fixture.host.setCornerRadius(7L, 4f)

        assertTrue(fixture.nativeCalls.isEmpty())
        fixture.takeTransaction().forEach { it() }
        assertEquals(listOf("frame:7:10:20:640:200", "radius:7:4.0"), fixture.nativeCalls)
    }

    @Test
    fun destructionInvalidatesAnAlreadyRetrievedTransactionAndDetachesBeforeRelease() {
        val fixture = Fixture()
        val token = Any()
        fixture.attach(7L, token)
        fixture.host.setFrame(7L, 0, 0, 640, 200, token)
        fixture.host.setCornerRadius(7L, 4f)
        // The render thread may already own this batch when Compose disposes the WebView.
        val transaction = fixture.takeTransaction()

        fixture.host.beforeDestroy(7L)
        fixture.liveViews.remove(7L)
        fixture.host.detach(7L, token) // NativeView.onDispose can run after WebView.onDispose.
        transaction.forEach { it() }

        assertEquals(listOf(7L), fixture.detached)
        assertTrue(fixture.nativeCalls.isEmpty())
    }

    @Test
    fun nativeViewDisposalBeforeWebViewDisposalAlsoDetachesOnlyOnce() {
        val fixture = Fixture()
        val token = Any()
        fixture.attach(7L, token)
        fixture.host.setFrame(7L, 0, 0, 640, 200, token)
        fixture.host.detach(7L, token)
        fixture.host.beforeDestroy(7L)
        fixture.liveViews.remove(7L)
        fixture.takeTransaction().forEach { it() }

        assertEquals(listOf(7L), fixture.detached)
        assertTrue(fixture.nativeCalls.isEmpty())
    }

    @Test
    fun reusedPointerAndTokenDoNotReviveLayoutFromAnOldMount() {
        val fixture = Fixture()
        val token = Any()
        fixture.attach(7L, token)
        fixture.host.setFrame(7L, 1, 2, 640, 200, token)
        fixture.host.setCornerRadius(7L, 4f)
        fixture.host.beforeDestroy(7L)
        fixture.attach(7L, token)
        fixture.host.setFrame(7L, 3, 4, 320, 100, token)
        fixture.host.setCornerRadius(7L, 8f)
        fixture.takeTransaction().forEach { it() }

        assertEquals(listOf("frame:7:3:4:320:100", "radius:7:8.0"), fixture.nativeCalls)
    }

    @Test
    fun destroyingOneViewPreservesOtherViewsInTheSameTransaction() {
        val fixture = Fixture()
        val first = Any()
        val second = Any()
        fixture.attach(7L, first)
        fixture.attach(8L, second)
        fixture.host.setFrame(7L, 0, 0, 640, 200, first)
        fixture.host.setFrame(8L, 0, 200, 640, 200, second)
        fixture.host.beforeDestroy(7L)
        fixture.liveViews.remove(7L)
        fixture.takeTransaction().forEach { it() }

        assertEquals(listOf("frame:8:0:200:640:200"), fixture.nativeCalls)
    }

    @Test
    fun pinnedNucleusHostStillExposesTheCapturedInteropScheduler() {
        // Load metadata only: this check must not initialize native windowing or launch a Tao window.
        val hostClass = Class.forName(
            "dev.nucleusframework.window.tao.scene.TaoComposeSceneHost\$nativeViewHost\$1",
            false,
            javaClass.classLoader,
        )
        assertEquals(TaoComposeSceneHost::class.java, findTaoSceneHostField(hostClass).type)
    }

    private class Fixture {
        val liveViews = mutableSetOf<Long>()
        val detached = mutableListOf<Long>()
        val nativeCalls = mutableListOf<String>()
        private val queue = mutableListOf<() -> Unit>()
        private val delegate = object : TaoNativeViewHost {
            override fun attach(childHandle: Long, regionToken: Any) = Unit

            override fun detach(childHandle: Long, regionToken: Any) {
                assertTrue(childHandle in liveViews, "Detaching a released native view")
                detached += childHandle
            }

            override fun setFrame(handle: Long, xPx: Int, yPx: Int, widthPx: Int, heightPx: Int, regionToken: Any) {
                error("The unsafe Nucleus layout queue must not receive the raw pointer")
            }

            override fun setCornerRadius(handle: Long, radiusPx: Float) {
                error("The unsafe Nucleus radius queue must not receive the raw pointer")
            }
        }
        val host = WebViewLifecycleHost(
            delegate = delegate,
            enqueue = { queue += it },
            setNativeFrame = { handle, x, y, width, height ->
                assertTrue(handle in liveViews, "Layout used a released native view")
                nativeCalls += "frame:$handle:$x:$y:$width:$height"
            },
            setNativeCornerRadius = { handle, radius ->
                assertTrue(handle in liveViews, "Radius used a released native view")
                nativeCalls += "radius:$handle:$radius"
            },
        )

        fun attach(handle: Long, token: Any) {
            liveViews += handle
            host.attach(handle, token)
        }

        fun takeTransaction(): List<() -> Unit> = queue.toList().also { queue.clear() }
    }
}
