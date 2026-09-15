package me.rerere.asr.providers

import okio.Buffer
import okio.GzipSink
import okio.GzipSource
import okio.buffer
import okio.use

internal fun gzipCompress(data: ByteArray): ByteArray {
    val output = Buffer()
    GzipSink(output).buffer().use { it.write(data) }
    return output.readByteArray()
}

internal fun gzipDecompress(data: ByteArray): ByteArray =
    GzipSource(Buffer().write(data)).buffer().use { it.readByteArray() }
