package me.rerere.rikkahub.platform

internal actual fun platformRenderQrCode(content: String, size: Int): Result<QrCodeMatrix> =
    Result.failure(UnsupportedOperationException("QR code rendering is unavailable on iOS"))
