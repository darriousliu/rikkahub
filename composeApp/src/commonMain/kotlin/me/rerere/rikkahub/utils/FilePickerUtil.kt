package me.rerere.rikkahub.utils

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile

interface FilePickerLauncher {
    fun launch(mimeTypes: Array<String> = arrayOf("*/*"))
}

@Composable
expect fun rememberFilePickerByMimeLauncher(
    onResult: (PlatformFile?) -> Unit,
): FilePickerLauncher
