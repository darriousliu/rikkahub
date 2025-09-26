package me.rerere.rikkahub.utils

import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode

actual class QRCodeResult(val result: QRResult)

actual class QRCodeScanner(
    private val launcher: ManagedActivityResultLauncher<Nothing?, QRResult>
) {
    actual fun startScanning() {
        launcher.launch(null)
    }
}

@Composable
actual fun rememberQRCodeScanner(onResult: (QRCodeResult) -> Unit): QRCodeScanner {
    val launcher = rememberLauncherForActivityResult(ScanQRCode()) {
        onResult(QRCodeResult(it))
    }
    return remember { QRCodeScanner(launcher) }
}
