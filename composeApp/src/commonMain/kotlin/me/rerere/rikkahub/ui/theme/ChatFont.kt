package me.rerere.rikkahub.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import me.rerere.rikkahub.data.datastore.ChatFontFamily
import me.rerere.rikkahub.data.datastore.DisplaySetting
import org.koin.compose.koinInject

val LocalChatFontFamily = staticCompositionLocalOf<FontFamily?> { null }

@Composable
fun ChatFontProvider(
    displaySetting: DisplaySetting,
    content: @Composable () -> Unit,
) {
    val chatFontFamily = rememberChatFontFamily(displaySetting)
    CompositionLocalProvider(LocalChatFontFamily provides chatFontFamily) {
        content()
    }
}

@Composable
fun rememberChatFontFamily(
    displaySetting: DisplaySetting,
    runtime: ChatFontRuntime = koinInject(),
): FontFamily {
    return remember(
        displaySetting.chatFontFamily,
        displaySetting.chatCustomFontPath,
        runtime,
    ) {
        displaySetting.resolveChatFontFamily(runtime)
    }
}

public fun DisplaySetting.resolveChatFontFamily(runtime: ChatFontRuntime): FontFamily = when (chatFontFamily) {
    ChatFontFamily.DEFAULT -> FontFamily.Default
    ChatFontFamily.SERIF -> FontFamily.Serif
    ChatFontFamily.MONOSPACE -> FontFamily.Monospace
    ChatFontFamily.CUSTOM -> runtime.load(chatCustomFontPath) ?: FontFamily.Default
}
