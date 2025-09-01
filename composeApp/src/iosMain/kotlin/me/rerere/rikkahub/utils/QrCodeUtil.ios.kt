package me.rerere.rikkahub.utils

import androidx.compose.runtime.Composable
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

actual typealias QRCodeResult = String

interface ProviderQRCodeScanner {
    fun factory(onResult: (QRCodeResult) -> Unit): QRCodeScanner
}

interface QRCodeDecoder {
    fun decode(uri: String): String?
}

@Composable
actual fun rememberQRCodeScanner(onResult: (QRCodeResult) -> Unit): QRCodeScanner {
    return koinInject<QRCodeScanner> { parametersOf(onResult) }
}
