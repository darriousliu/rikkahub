package me.rerere.rikkahub.platform

import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.io.File
import java.net.URI

public class JvmExternalUriOpener : ExternalUriOpener {
    override fun open(uri: String): Result<Unit> = runCatching {
        check(!GraphicsEnvironment.isHeadless()) { "External URI opening is unavailable in headless mode" }
        check(Desktop.isDesktopSupported()) { "Desktop integration is unavailable" }

        val desktop = Desktop.getDesktop()
        val target = URI(uri)
        if (target.scheme.equals("file", ignoreCase = true)) {
            check(desktop.isSupported(Desktop.Action.OPEN)) { "Desktop file opening is unavailable" }
            desktop.open(File(target))
        } else {
            check(desktop.isSupported(Desktop.Action.BROWSE)) { "Desktop URI browsing is unavailable" }
            desktop.browse(target)
        }
    }
}
