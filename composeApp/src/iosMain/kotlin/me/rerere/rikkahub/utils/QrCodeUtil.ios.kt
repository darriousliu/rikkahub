package me.rerere.rikkahub.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual typealias QRCodeResult = String

actual class QRCodeScanner() {
    actual fun startScanning() {
    }
}

@Composable
actual fun rememberQRCodeScanner(onResult: (QRCodeResult) -> Unit): QRCodeScanner {
    return remember { QRCodeScanner() }
}
