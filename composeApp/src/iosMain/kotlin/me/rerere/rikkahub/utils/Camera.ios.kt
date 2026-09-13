package me.rerere.rikkahub.utils

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile

@Composable
internal actual fun rememberCameraLauncher(
    onCaptured: (PlatformFile, cleanup: () -> Unit) -> Unit,
): (() -> Unit)? = null
