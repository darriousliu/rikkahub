package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import coil3.Uri
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.PlatformContext
import me.rerere.rikkahub.ui.hooks.ChatInputState

@Composable
internal actual fun ImagePickButton(onAddImages: (List<Uri>) -> Unit) {
    // TODO
}

@Composable
actual fun TakePicButton(onAddImages: (List<Uri>) -> Unit) {
    // TODO
}

@Composable
actual fun FilePickButton(onAddFiles: (List<UIMessagePart.Document>) -> Unit) {
    // TODO
}

@Stable
internal actual fun provideDialogProperties(): DialogProperties {
    return DialogProperties(
        usePlatformDefaultWidth = false,
    )
}

internal actual fun createImageReceiveListener(
    state: ChatInputState,
    context: PlatformContext
): ReceiveContentListener {
    return ReceiveContentListener { it }
}

internal actual fun Modifier.platformContentReceiver(listener: ReceiveContentListener): Modifier {
    return this
}
