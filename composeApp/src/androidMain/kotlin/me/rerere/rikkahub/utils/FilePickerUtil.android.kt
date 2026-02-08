package me.rerere.rikkahub.utils

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.vinceglb.filekit.PlatformFile

@Composable
actual fun rememberFilePickerByMimeLauncher(
    onResult: (PlatformFile?) -> Unit
): FilePickerLauncher {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }
        val pickedFile = PlatformFile(uri)
        onResult(pickedFile)
    }
    return remember {
        object : FilePickerLauncher {
            override fun launch(mimeTypes: Array<String>) {
                launcher.launch(mimeTypes)
            }
        }
    }
}
