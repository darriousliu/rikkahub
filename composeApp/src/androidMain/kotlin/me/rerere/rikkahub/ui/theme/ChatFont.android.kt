package me.rerere.rikkahub.ui.theme

import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import kotlinx.io.files.Path

internal actual fun loadCustomFont(file: Path): FontFamily {
    val fontFile = File(file.toString())
    Typeface.createFromFile(fontFile)
    return FontFamily(Font(fontFile))
}
