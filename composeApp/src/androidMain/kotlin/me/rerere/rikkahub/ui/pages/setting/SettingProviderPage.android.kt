package me.rerere.rikkahub.ui.pages.setting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import com.dokar.sonner.ToastType
import com.dokar.sonner.ToasterState
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import me.rerere.ai.provider.ProviderSetting
import me.rerere.common.PlatformContext
import me.rerere.rikkahub.ui.components.ui.decodeProviderSetting
import me.rerere.rikkahub.utils.ImageUtils
import org.jetbrains.compose.resources.getString
import rikkahub.composeapp.generated.resources.*

@Composable
actual fun rememberQrCodeLauncher(onResult: (Any) -> Unit): Any {
    return rememberLauncherForActivityResult(ScanQRCode()) {
        onResult(it)
    }
}

internal actual suspend fun handleQRResult(
    result: Any,
    onAdd: (ProviderSetting) -> Unit,
    toaster: ToasterState,
    context: PlatformContext
) {
    runCatching {
        result as QRResult
        when (result) {
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

internal actual suspend fun handleImageQRCode(
    uri: PlatformFile,
    onAdd: (ProviderSetting) -> Unit,
    toaster: ToasterState,
    context: PlatformContext
) {
    runCatching {
        // 使用ImageUtils解析二维码
        val qrContent = ImageUtils.decodeQRCodeFromUri(context, uri.absolutePath())

        if (qrContent.isNullOrEmpty()) {
            toaster.show(
                getString(Res.string.setting_provider_page_no_qr_found),
                type = ToastType.Error
            )
            return
        }

        val setting = decodeProviderSetting(qrContent)
        onAdd(setting)
        toaster.show(
            getString(Res.string.setting_provider_page_import_success),
            type = ToastType.Success
        )
    }.onFailure { error ->
        toaster.show(
            getString(Res.string.setting_provider_page_image_qr_decode_failed, error.message ?: ""),
            type = ToastType.Error
        )
    }
}
