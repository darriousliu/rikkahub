package me.rerere.asr.providers

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class GzipCompatibilityTest {
    private val payloads = listOf(
        byteArrayOf(),
        """{"result":{"text":"中文转写 🎙️"}}""".encodeToByteArray(),
        Random(86).nextBytes(32_791),
    )

    @Test
    fun sharedCompressionIsReadableByTheOriginalJavaDecoder() {
        payloads.forEach { payload ->
            val decoded = GZIPInputStream(gzipCompress(payload).inputStream()).use { it.readBytes() }
            assertContentEquals(payload, decoded)
        }
    }

    @Test
    fun sharedDecoderReadsTheOriginalJavaCompression() {
        payloads.forEach { payload ->
            val compressed = ByteArrayOutputStream().apply {
                GZIPOutputStream(this).use { it.write(payload) }
            }.toByteArray()
            assertContentEquals(payload, gzipDecompress(compressed))
        }
    }

    @Test
    fun corruptedAndTruncatedPayloadsStillFail() {
        val compressed = gzipCompress(payloads.last())
        val invalidCrc = compressed.copyOf().apply {
            this[size - 8] = (this[size - 8].toInt() xor 1).toByte()
        }
        listOf(invalidCrc, compressed.copyOf(compressed.size - 1)).forEach { invalid ->
            assertFailsWith<IOException> { gzipDecompress(invalid) }
            assertFailsWith<IOException> { GZIPInputStream(invalid.inputStream()).use { it.readBytes() } }
        }
    }
}
