package me.rerere.rikkahub.platform

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

internal actual fun platformRenderQrCode(content: String, size: Int): Result<QrCodeMatrix> = runCatching {
    require(size > 0) { "QR code size must be positive" }
    val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    QrCodeMatrix(
        width = bitMatrix.width,
        height = bitMatrix.height,
        darkModules = BooleanArray(bitMatrix.width * bitMatrix.height) { index ->
            bitMatrix[index % bitMatrix.width, index / bitMatrix.width]
        },
    )
}
