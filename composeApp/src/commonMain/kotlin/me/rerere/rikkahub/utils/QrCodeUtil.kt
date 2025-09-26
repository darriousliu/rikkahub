package me.rerere.rikkahub.utils

import androidx.compose.runtime.Composable

expect class QRCodeResult

expect class QRCodeScanner {
    fun startScanning()
}

@Composable
expect fun rememberQRCodeScanner(onResult: (QRCodeResult) -> Unit): QRCodeScanner
