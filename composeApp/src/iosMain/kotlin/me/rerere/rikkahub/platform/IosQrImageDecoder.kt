package me.rerere.rikkahub.platform

internal actual suspend fun platformDecodeQrImage(imageBytes: ByteArray): QrScanResult =
    decodeQrImageWithKScan(imageBytes)
