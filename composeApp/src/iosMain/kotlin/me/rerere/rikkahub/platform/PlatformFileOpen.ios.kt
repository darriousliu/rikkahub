package me.rerere.rikkahub.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.koin.compose.koinInject

@Composable
internal actual fun rememberFileOpener(): (String) -> Result<Unit> {
    val opener = koinInject<ExternalUriOpener>()
    return remember(opener) { opener::open }
}
