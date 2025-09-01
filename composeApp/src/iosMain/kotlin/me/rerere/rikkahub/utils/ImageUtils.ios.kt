package me.rerere.rikkahub.utils

import me.rerere.common.PlatformContext

actual object ImageUtils {
//    suspend fun loadOptimizedBitmap(uri: String, maxSize: Int): ByteArray? {
//        return try {
//            val url = NSURL.fileURLWithPath(uri)
//            val source = CGImageSourceCreateWithURL(url.retainBridgeAs(), null) ?: return null
//            val options = NSDictionary.create(
//                mapOf(
//                    kCGImageSourceShouldCache to false,
//                    kCGImageSourceCreateThumbnailFromImageAlways to true,
//                    kCGImageSourceCreateThumbnailWithTransform to true,
//                    kCGImageSourceThumbnailMaxPixelSize to maxSize.toDouble()
//                )
//            )
//
//            val cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0u, options.retainBridgeAs()) ?: return null
//            val uiImage = UIImage(cGImage = cgImage)
//
//            // 转为 PNG Data
//            uiImage()?.toByteArray()
//        } catch (e: Exception) {
//            e.printStackTrace()
//            null
//        }
//    }
//
//    suspend fun decodeQRCodeFromUri(uri: String, maxSize: Int): String? {
//        return try {
//            val url = NSURL.fileURLWithPath(uri)
//            val image = CIImage(contentsOfURL = url)
//            val detector = CIDetector(
//                type = CIDetectorTypeQRCode,
//                null,
//                mapOf(CIDetectorAccuracy to CIDetectorAccuracyHigh)
//            ) ?: return null
//
//            val features = detector.featuresInImage(image)
//            for (feature in features) {
//                if (feature is CIQRCodeFeature) {
//                    return feature.messageString
//                }
//            }
//            null
//        } catch (e: Exception) {
//            e.printStackTrace()
//            null
//        }
//    }

    actual fun getTavernCharacterMeta(context: PlatformContext, uri: String): Result<String> {
        TODO("Not yet implemented")
    }

    actual fun decodeQRCodeFromUri(context: PlatformContext, uri: String, maxSize: Int): String? {
        TODO("Not yet implemented")
    }
}
