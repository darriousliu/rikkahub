package me.rerere.rikkahub.ui.pages.setting

import com.dokar.sonner.ToastType
import com.dokar.sonner.ToasterState
import io.github.g00fy2.quickie.QRResult
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
        when (val result = result.result) {
            is QRResult.QRError -> {
                toaster.show(
                    getString(
                        Res.string.setting_provider_page_scan_error,
                        result
                    ), type = ToastType.Error
                )
            }

            QRResult.QRMissingPermission -> {
                toaster.show(
                    getString(Res.string.setting_provider_page_no_permission),
                    type = ToastType.Error
                )
            }

            is QRResult.QRSuccess -> {
                val setting = decodeProviderSetting(result.content.rawValue ?: "")
                onAdd(setting)
                toaster.show(
                    getString(Res.string.setting_provider_page_import_success),
                    type = ToastType.Success
                )
            }

            QRResult.QRUserCanceled -> {}
        }
    }.onFailure { error ->
        toaster.show(
            getString(Res.string.setting_provider_page_qr_decode_failed, error.message ?: ""),
            type = ToastType.Error
        )
    }
}
