package me.rerere.rikkahub.platform

import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.net.URI

public class JvmExternalUriOpener : ExternalUriOpener {
    override fun open(uri: String): Result<Unit> = runCatching {
        check(!GraphicsEnvironment.isHeadless()) { "External URI opening is unavailable in headless mode" }
        check(Desktop.isDesktopSupported()) { "Desktop integration is unavailable" }

        val desktop = Desktop.getDesktop()
        check(desktop.isSupported(Desktop.Action.BROWSE)) { "Desktop URI browsing is unavailable" }
        desktop.browse(URI(uri))
    }
}
