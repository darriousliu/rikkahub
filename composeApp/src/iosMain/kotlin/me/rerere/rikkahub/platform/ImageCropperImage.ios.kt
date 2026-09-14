package me.rerere.rikkahub.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.withScopedAccess
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import org.jetbrains.skia.Image
import org.jetbrains.skia.impl.use
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFURLCreateWithFileSystemPath
import platform.CoreFoundation.kCFBooleanFalse
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFURLPOSIXPathStyle
import platform.CoreGraphics.CGImageRelease
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithURL
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceShouldCache
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
internal actual fun readCropImage(source: PlatformFile, maxSize: Int): ImageBitmap = source.withScopedAccess {
    memScoped {
        val path = checkNotNull(CFStringCreateWithCString(null, source.nsUrl.path!!, kCFStringEncodingUTF8))
        val url = try { checkNotNull(CFURLCreateWithFileSystemPath(null, path, kCFURLPOSIXPathStyle, false)) }
            finally { CFRelease(path) }
        val options = checkNotNull(CFDictionaryCreateMutable(null, 4, null, null))
        val limit = alloc<IntVar> { value = maxSize }
        val number = checkNotNull(CFNumberCreate(null, kCFNumberIntType, limit.ptr))
        try {
            CFDictionarySetValue(options, kCGImageSourceShouldCache, kCFBooleanFalse)
            CFDictionarySetValue(options, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
            CFDictionarySetValue(options, kCGImageSourceCreateThumbnailWithTransform, kCFBooleanTrue)
            CFDictionarySetValue(options, kCGImageSourceThumbnailMaxPixelSize, number)
            val imageSource = checkNotNull(CGImageSourceCreateWithURL(url, options)) { "Unable to read image" }
            try {
                val thumbnail = checkNotNull(CGImageSourceCreateThumbnailAtIndex(imageSource, 0u, options)) {
                    "Unable to sample image"
                }
                try {
                    // Only the bounded thumbnail is bridged to Skia; the original raster is never allocated here.
                    val data = checkNotNull(UIImagePNGRepresentation(UIImage.imageWithCGImage(thumbnail)))
                    val bytes = ByteArray(data.length.toInt())
                    bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
                    Image.makeFromEncoded(bytes).use { it.toComposeImageBitmap() }
                } finally { CGImageRelease(thumbnail) }
            } finally { CFRelease(imageSource) }
        } finally {
            CFRelease(number)
            CFRelease(options)
            CFRelease(url)
        }
    }
}
