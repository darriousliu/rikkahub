package me.rerere.rikkahub.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreImage.CIContext
import platform.CoreImage.CIFilter
import platform.CoreImage.filterWithName
import platform.CoreImage.kCIFormatL8
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.setValue

private const val QR_QUIET_ZONE_MODULES = 4

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
internal actual fun platformRenderQrCode(content: String, size: Int): Result<QrCodeMatrix> = runCatching {
    require(content.isNotEmpty()) { "QR code content must not be empty" }
    require(size > 0) { "QR code size must be positive" }

    val contentBytes = content.encodeToByteArray()
    val inputData = contentBytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = contentBytes.size.toULong())
    }
    val filter = checkNotNull(CIFilter.filterWithName("CIQRCodeGenerator")) {
        "CIQRCodeGenerator is unavailable"
    }
    filter.setValue(inputData, forKey = "inputMessage")
    filter.setValue("M", forKey = "inputCorrectionLevel")
    val image = checkNotNull(filter.outputImage) { "CIQRCodeGenerator did not produce an image" }
    val extent = image.extent
    val rawWidth = extent.useContents { this.size.width.toInt() }
    val rawHeight = extent.useContents { this.size.height.toInt() }
    require(rawWidth > 0 && rawHeight > 0) { "CIQRCodeGenerator produced an empty image" }

    val pixels = ByteArray(rawWidth * rawHeight)
    pixels.usePinned { pinned ->
        CIContext.contextWithOptions(null).render(
            image = image,
            toBitmap = pinned.addressOf(0),
            rowBytes = rawWidth.toLong(),
            bounds = extent,
            format = kCIFormatL8,
            colorSpace = null,
        )
    }

    val width = rawWidth + QR_QUIET_ZONE_MODULES * 2
    val height = rawHeight + QR_QUIET_ZONE_MODULES * 2
    QrCodeMatrix(
        width = width,
        height = height,
        darkModules = BooleanArray(width * height) { index ->
            val x = index % width - QR_QUIET_ZONE_MODULES
            val y = index / width - QR_QUIET_ZONE_MODULES
            x in 0 until rawWidth && y in 0 until rawHeight && pixels[y * rawWidth + x].toInt() == 0
        },
    )
}
