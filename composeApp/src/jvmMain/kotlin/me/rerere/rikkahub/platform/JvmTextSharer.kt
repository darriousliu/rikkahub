package me.rerere.rikkahub.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
public actual fun rememberPlatformTextSharer(): TextSharer = remember {
    TextSharer { text ->
        runCatching {
            check(!GraphicsEnvironment.isHeadless()) { "Clipboard sharing is unavailable in headless mode" }
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        }
    }
}
