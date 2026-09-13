package me.rerere.rikkahub.utils

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile

/** The caller releases the temporary photo after copying or cropping it. */
@Composable
internal expect fun rememberCameraLauncher(
    onCaptured: (PlatformFile, cleanup: () -> Unit) -> Unit,
): (() -> Unit)?
