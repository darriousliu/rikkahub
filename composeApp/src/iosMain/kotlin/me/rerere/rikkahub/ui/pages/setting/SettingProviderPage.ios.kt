package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.runtime.Composable
import com.dokar.sonner.ToasterState
import io.github.vinceglb.filekit.PlatformFile
import me.rerere.ai.provider.ProviderSetting
import me.rerere.common.PlatformContext

@Composable
actual fun rememberQrCodeLauncher(onResult: (Any) -> Unit): Any {
    TODO("Not yet implemented")
}

internal actual suspend fun handleImageQRCode(
    uri: PlatformFile,
    onAdd: (ProviderSetting) -> Unit,
    toaster: ToasterState,
    context: PlatformContext
) {
}

internal actual suspend fun handleQRResult(
    result: Any,
    onAdd: (ProviderSetting) -> Unit,
    toaster: ToasterState,
    context: PlatformContext
) {
}
