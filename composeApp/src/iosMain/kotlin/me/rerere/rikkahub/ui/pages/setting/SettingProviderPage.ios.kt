package me.rerere.rikkahub.ui.pages.setting

import com.dokar.sonner.ToastType
import com.dokar.sonner.ToasterState
import me.rerere.ai.provider.ProviderSetting
import me.rerere.common.PlatformContext
import me.rerere.rikkahub.ui.components.ui.decodeProviderSetting
import me.rerere.rikkahub.utils.QRCodeResult
import org.jetbrains.compose.resources.getString
import rikkahub.composeapp.generated.resources.*

internal actual suspend fun handleQRResult(
    result: QRCodeResult,
    onAdd: (ProviderSetting) -> Unit,
    toaster: ToasterState,
    context: PlatformContext
) {
    runCatching {
        val setting = decodeProviderSetting(result)
        onAdd(setting)
        toaster.show(
            getString(Res.string.setting_provider_page_import_success),
            type = ToastType.Success
        )
    }.onFailure { error ->
        toaster.show(
            getString(Res.string.setting_provider_page_qr_decode_failed, error.message ?: ""),
            type = ToastType.Error
        )
    }
}
