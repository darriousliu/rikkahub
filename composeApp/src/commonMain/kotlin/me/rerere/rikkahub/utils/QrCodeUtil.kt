package me.rerere.rikkahub.utils

import androidx.compose.runtime.Composable

expect class QRCodeResult

interface QRCodeScanner {
    fun startScanning()
}

@Composable
expect fun rememberQRCodeScanner(onResult: (QRCodeResult) -> Unit): QRCodeScanner
