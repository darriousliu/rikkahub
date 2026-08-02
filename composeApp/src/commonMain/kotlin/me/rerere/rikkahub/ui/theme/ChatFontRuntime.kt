package me.rerere.rikkahub.ui.theme

import androidx.compose.ui.text.font.FontFamily
import io.github.vinceglb.filekit.PlatformFile

public data class ImportedChatFont(
    public val relativePath: String,
    public val displayName: String,
)

public interface ChatFontRuntime {
    public val canImportCustomFont: Boolean

    public suspend fun import(source: PlatformFile): Result<ImportedChatFont>

    public fun delete(relativePath: String): Result<Unit>

    public fun load(relativePath: String): FontFamily?
}

public object UnavailableChatFontRuntime : ChatFontRuntime {
    override val canImportCustomFont: Boolean = false

    override suspend fun import(source: PlatformFile): Result<ImportedChatFont> =
        Result.failure(UnsupportedOperationException("Custom fonts are unavailable on this platform"))

    override fun delete(relativePath: String): Result<Unit> = Result.success(Unit)

    override fun load(relativePath: String): FontFamily? = null
}
