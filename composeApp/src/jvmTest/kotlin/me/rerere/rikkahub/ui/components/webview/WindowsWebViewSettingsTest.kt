package me.rerere.rikkahub.ui.components.webview

import dev.nucleusframework.webview.web.WebContent
import dev.nucleusframework.webview.web.WebViewState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WindowsWebViewSettingsTest {
    @Test
    fun inlineChartsShareAnApplicationDataDirectoryInsteadOfTheJavaExecutableDirectory() {
        val applicationDirectory = File("build/test-user/用户/RikkaHub")
        val first = WebViewState(WebContent.Data("<html>first chart</html>"))
        val second = WebViewState(WebContent.Data("<html>regenerated chart</html>"))
        configureWindowsWebViewDataDirectory(first, applicationDirectory)
        configureWindowsWebViewDataDirectory(second, applicationDirectory)

        val expected = File(applicationDirectory, "webview2").absolutePath
        assertEquals(expected, first.webSettings.desktopWebSettings.dataDirectory)
        assertEquals(expected, second.webSettings.desktopWebSettings.dataDirectory)
        assertEquals(WebContent.Data("<html>first chart</html>"), first.content)
    }

    @Test
    fun explicitProfileAndIncognitoSettingsArePreserved() {
        val custom = WebViewState(WebContent.NavigatorOnly)
        custom.webSettings.desktopWebSettings.dataDirectory = "custom-profile"
        val incognito = WebViewState(WebContent.NavigatorOnly)
        incognito.webSettings.desktopWebSettings.incognito = true

        configureWindowsWebViewDataDirectory(custom, File("app-data"))
        configureWindowsWebViewDataDirectory(incognito, File("app-data"))

        assertEquals("custom-profile", custom.webSettings.desktopWebSettings.dataDirectory)
        assertNull(incognito.webSettings.desktopWebSettings.dataDirectory)
    }
}
