package me.rerere.rikkahub.ui.pages.setting

import com.dokar.sonner.ToasterState
import io.github.vinceglb.filekit.PlatformFile
import me.rerere.ai.provider.ProviderSetting
import me.rerere.common.PlatformContext
import me.rerere.rikkahub.utils.QRCodeResult

internal actual suspend fun handleImageQRCode(
    uri: PlatformFile,
    onAdd: (ProviderSetting) -> Unit,
    toaster: ToasterState,
    context: PlatformContext
) {
    // TODO("Not yet implemented")
}

internal actual suspend fun handleQRResult(
    result: QRCodeResult,
    onAdd: (ProviderSetting) -> Unit,
    toaster: ToasterState,
    context: PlatformContext
) {
    // TODO("Not yet implemented")
}
