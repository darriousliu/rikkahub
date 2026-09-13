package me.rerere.rikkahub.platform

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.core.net.toUri

@Composable
internal actual fun rememberFileOpener(): (String) -> Result<Unit> {
    val context = LocalContext.current
    return remember(context) {
        { uri ->
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    data = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        uri.toUri().toFile(),
                    )
                }
                context.startActivity(Intent.createChooser(intent, null))
            }
        }
    }
}
