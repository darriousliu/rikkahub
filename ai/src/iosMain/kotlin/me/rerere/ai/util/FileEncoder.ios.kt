package me.rerere.ai.util

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.utils.retainBridgeAs
import platform.CoreServices.kUTTypeJPEG
import platform.Foundation.*
import platform.ImageIO.*

actual fun UIMessagePart.Image.encodeBase64(withPrefix: Boolean): Result<EncodedImage> = runCatching {
    when {
        this.url.startsWith("file://") -> {
            val nsUrl = NSURL(string = this.url)

            val filePath = nsUrl.path ?: throw IllegalArgumentException("Invalid file path: ${this.url}")
            val nsData = NSData.dataWithContentsOfFile(filePath)
                ?: throw IllegalArgumentException("File does not exist or cannot be read: ${this.url}")

            val mimeType = guessMimeTypeFromData(nsData)

            // 如果不是支持的格式，转换为JPEG
            val processedData = if (mimeType !in supportedTypes) {
                convertToJpeg(nsData) ?: throw IllegalArgumentException("Failed to convert image to JPEG")
            } else {
                nsData
            }

            val finalMimeType = if (mimeType !in supportedTypes) "image/jpeg" else mimeType
            val encoded = processedData.base64EncodedStringWithOptions(0u)

            EncodedImage(
                base64 = if (withPrefix) "data:$finalMimeType;base64,$encoded" else encoded,
                mimeType = finalMimeType
            )
        }

        this.url.startsWith("data:") -> {
            // 从 data URL 提取 mime type
            val mimeType = url.substringAfter("data:").substringBefore(";")
            EncodedImage(base64 = url, mimeType = mimeType)
        }

        this.url.startsWith("http:") -> EncodedImage(base64 = url, mimeType = "image/png")
        else -> throw IllegalArgumentException("Unsupported URL format: $url")
    }
}

actual fun UIMessagePart.Video.encodeBase64(withPrefix: Boolean): Result<String> {
    TODO("Not yet implemented")
}

actual fun UIMessagePart.Audio.encodeBase64(withPrefix: Boolean): Result<String> {
    TODO("Not yet implemented")
}

private fun convertToJpeg(imageData: NSData): NSData? {
    // 创建CGImageSource
    val imageSource = CGImageSourceCreateWithData(imageData.retainBridgeAs(), null)
        ?: return null

    // 获取第一张图片
    val cgImage = CGImageSourceCreateImageAtIndex(imageSource, 0u, null)
        ?: return null

    // 创建可变数据用于存储JPEG
    val mutableData = NSMutableData()

    // 创建CGImageDestination，指定JPEG格式
    val imageDestination = CGImageDestinationCreateWithData(
        mutableData.retainBridgeAs(),
        kUTTypeJPEG,
        1u,
        null
    ) ?: return null

    // 设置JPEG压缩质量
    val options = mapOf(
        kCGImageDestinationLossyCompressionQuality to 0.8
    )
    val optionsDict = options.toNSDictionary()

    // 添加图片到destination
    CGImageDestinationAddImage(imageDestination, cgImage, optionsDict.retainBridgeAs())

    // 完成写入
    return if (CGImageDestinationFinalize(imageDestination)) {
        mutableData.copy() as NSData
    } else {
        null
    }
}

private fun guessMimeTypeFromData(data: NSData): String {
    if (data.length < 16u) {
        throw IllegalArgumentException("File too short to determine MIME type")
    }

    val bytes = ByteArray(16)
    memScoped {
        val bytesPtr = allocArray<ByteVar>(16)
        data.getBytes(bytesPtr, 16u)
        for (i in 0 until 16) {
            bytes[i] = bytesPtr[i]
        }
    }

    // 打印前16个字节（可选，用于调试）
    println("guessMimeType bytes = ${bytes.joinToString(",")}")

    // 判断 HEIC 格式：包含 "ftypheic"
    val heicSignature = bytes.copyOfRange(4, 12)
    if (heicSignature.decodeToString() == "ftypheic") {
        return "image/heic"
    }

    // 判断 JPEG 格式：开头为 0xFF 0xD8
    if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
        return "image/jpeg"
    }

    // 判断 PNG 格式：开头为 89 50 4E 47 0D 0A 1A 0A
    if (bytes.copyOfRange(0, 8).contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        )
    ) {
        return "image/png"
    }

    // 判断WebP格式：开头为 "RIFF" + 4字节长度 + "WEBP"
    val riffSignature = bytes.copyOfRange(0, 4)
    val webpSignature = bytes.copyOfRange(8, 12)
    if (riffSignature.decodeToString() == "RIFF" && webpSignature.decodeToString() == "WEBP") {
        return "image/webp"
    }

    // 判断 GIF 格式：开头为 "GIF89a" 或 "GIF87a"
    val gifSignature = bytes.copyOfRange(0, 6)
    val header = gifSignature.decodeToString()
    if (header == "GIF89a" || header == "GIF87a") {
        return "image/gif"
    }

    throw IllegalArgumentException(
        "Failed to guess MIME type: $header, ${
            bytes.joinToString(",") { it.toUByte().toString() }
        }"
    )
}

// 扩展函数：将Map转换为NSDictionary
private fun Map<*, *>.toNSDictionary(): NSDictionary = NSDictionary.create(this)
