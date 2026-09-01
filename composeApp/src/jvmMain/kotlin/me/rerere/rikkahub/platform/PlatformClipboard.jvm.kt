package me.rerere.rikkahub.platform

import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

internal actual object PlatformClipboard {
    actual fun readText(): String = runCatching {
        check(!GraphicsEnvironment.isHeadless()) { "Clipboard is unavailable in headless mode" }
        Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
    }.getOrNull().orEmpty()

    actual fun writeText(text: String) {
        check(!GraphicsEnvironment.isHeadless()) { "Clipboard is unavailable in headless mode" }
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }
}
