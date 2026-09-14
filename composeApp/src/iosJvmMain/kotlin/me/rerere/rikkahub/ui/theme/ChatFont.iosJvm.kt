package me.rerere.rikkahub.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.jetbrains.skia.FontMgr

internal actual fun loadCustomFont(file: Path): FontFamily {
    requireNotNull(FontMgr.default.makeFromFile(file.toString())) { "Invalid font file" }.close()
    val bytes = SystemFileSystem.source(file).buffered().use { it.readByteArray() }
    return FontFamily(Font(identity = file.toString(), data = bytes))
}
