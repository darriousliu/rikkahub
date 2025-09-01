package me.rerere.rikkahub.utils

import androidx.compose.runtime.Composable
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import platform.UIKit.UIImage

actual typealias QRCodeResult = String

interface ProviderQRCodeScanner {
    fun factory(onResult: (QRCodeResult) -> Unit): QRCodeScanner
}

interface QRCodeEncoder {
    fun encode(data: String, size: Int, color: String, backgroundColor: String): UIImage
}

interface QRCodeDecoder {
    fun decode(uri: String): String?
}

@Composable
actual fun rememberQRCodeScanner(onResult: (QRCodeResult) -> Unit): QRCodeScanner {
    return koinInject<QRCodeScanner> { parametersOf(onResult) }
}
