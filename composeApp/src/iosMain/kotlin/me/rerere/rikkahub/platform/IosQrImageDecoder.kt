package me.rerere.rikkahub.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreImage.CIContext
import platform.CoreImage.CIDetector
import platform.CoreImage.CIDetectorAccuracy
import platform.CoreImage.CIDetectorAccuracyHigh
import platform.CoreImage.CIDetectorTypeQRCode
import platform.CoreImage.CIImage
import platform.CoreImage.CIQRCodeFeature
import platform.CoreImage.kCIContextUseSoftwareRenderer
import platform.Foundation.NSData
import platform.Foundation.create

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
internal actual suspend fun platformDecodeQrImage(imageBytes: ByteArray): QrScanResult =
    withContext(Dispatchers.Default) {
        runCatching {
            val data = imageBytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = imageBytes.size.toULong())
            }
            val image = checkNotNull(CIImage.imageWithData(data)) { "Failed to decode image bytes" }
            // KScan's Vision detector can fail to create an inference context on the iOS simulator.
            val context = CIContext.contextWithOptions(mapOf(kCIContextUseSoftwareRenderer to true))
            val detector = checkNotNull(
                CIDetector.detectorOfType(
                    CIDetectorTypeQRCode,
                    context,
                    mapOf(CIDetectorAccuracy to CIDetectorAccuracyHigh),
                )
            ) { "QR code detector is unavailable" }
            val content = detector.featuresInImage(image)
                .filterIsInstance<CIQRCodeFeature>()
                .firstNotNullOfOrNull { it.messageString }
                ?: error("No barcode found in image")
            QrScanResult.Success(content)
        }.getOrElse { QrScanResult.Failure(it) }
    }
