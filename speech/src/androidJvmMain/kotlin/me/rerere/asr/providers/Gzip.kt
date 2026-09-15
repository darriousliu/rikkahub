package me.rerere.asr.providers

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

internal actual fun gzipCompress(data: ByteArray): ByteArray {
    val bos = ByteArrayOutputStream()
    GZIPOutputStream(bos).use { it.write(data) }
    return bos.toByteArray()
}

internal actual fun gzipDecompress(data: ByteArray): ByteArray =
    GZIPInputStream(data.inputStream()).use { it.readBytes() }
