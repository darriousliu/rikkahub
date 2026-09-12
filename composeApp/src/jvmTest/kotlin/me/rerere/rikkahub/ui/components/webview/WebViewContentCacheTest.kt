package me.rerere.rikkahub.ui.components.webview

import kotlinx.io.files.Path
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class WebViewContentCacheTest {
    private val root = Files.createTempDirectory("webview-cache-").toFile()
    private val cacheDir = Path(root.path)

    @AfterTest
    fun cleanUp() { root.deleteRecursively() }

    @Test
    fun storesLargeContentOutsideNavigationState() {
        val content = "<html>${"preview".repeat(50_000)}</html>"
        val id = WebViewContentCache.store(cacheDir, content)
        assertEquals(64, id.length)
        assertEquals(content, root.resolve("webview_content/$id").readText())
        assertEquals(content, WebViewContentCache.load(cacheDir, id))
    }

    @Test
    fun reusesTheSameCacheEntryForIdenticalContent() {
        val content = "<html>preview</html>"
        val firstId = WebViewContentCache.store(cacheDir, content)
        val secondId = WebViewContentCache.store(cacheDir, content)
        assertEquals(firstId, secondId)
        assertEquals(1, root.resolve("webview_content").listFiles()?.size)
    }

    @Test
    fun rejectsInvalidCacheIds() {
        assertNull(WebViewContentCache.load(cacheDir, "../content"))
    }

    @Test
    fun retainsMoreThan64PreviewsAndTouchesReadEntriesBeforeExpiry() {
        var time = 10.days.inWholeMilliseconds
        val old = WebViewContentCache.store(cacheDir, "expired") { time }
        val active = WebViewContentCache.store(cacheDir, "active") { time }
        time += 6.days.inWholeMilliseconds
        assertEquals("active", WebViewContentCache.load(cacheDir, active) { time })
        time += 2.days.inWholeMilliseconds
        repeat(70) { WebViewContentCache.store(cacheDir, "preview-$it") { time } }
        assertFalse(root.resolve("webview_content/$old").exists())
        assertTrue(root.resolve("webview_content/$active").exists())
        assertEquals(71, root.resolve("webview_content").listFiles()?.size)
    }
}
