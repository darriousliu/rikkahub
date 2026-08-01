package me.rerere.common.archive

import kotlinx.io.Sink
import kotlinx.io.Source
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

actual object PlatformZipArchive : ZipArchive by CommonZipArchive(JvmRawDeflateCodec)

private object JvmRawDeflateCodec : RawDeflateCodec {
    override fun deflate(
        source: Source,
        sink: Sink,
        onUncompressedBytes: (ByteArray, Int) -> Unit,
    ): RawDeflateResult {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        val input = ByteArray(DEFAULT_BUFFER_SIZE)
        val output = ByteArray(DEFAULT_BUFFER_SIZE)
        var inputByteCount = 0L
        var outputByteCount = 0L
        try {
            while (true) {
                val read = source.readAtMostTo(input)
                if (read == -1) break
                onUncompressedBytes(input, read)
                inputByteCount += read
                deflater.setInput(input, 0, read)
                while (!deflater.needsInput()) {
                    val produced = deflater.deflate(output)
                    if (produced > 0) {
                        sink.write(output, 0, produced)
                        outputByteCount += produced
                    } else if (!deflater.needsInput()) {
                        throw ZipArchiveException("Raw deflate encoder made no progress")
                    }
                }
            }

            deflater.finish()
            while (!deflater.finished()) {
                val produced = deflater.deflate(output)
                if (produced == 0) throw ZipArchiveException("Raw deflate encoder did not finish")
                sink.write(output, 0, produced)
                outputByteCount += produced
            }
            return RawDeflateResult(inputByteCount, outputByteCount)
        } finally {
            deflater.end()
        }
    }

    override fun inflate(
        source: Source,
        sink: Sink,
        onUncompressedBytes: (ByteArray, Int) -> Unit,
    ): RawDeflateResult {
        val inflater = Inflater(true)
        val input = ByteArray(DEFAULT_BUFFER_SIZE)
        val output = ByteArray(DEFAULT_BUFFER_SIZE)
        var inputByteCount = 0L
        var outputByteCount = 0L
        try {
            while (!inflater.finished()) {
                if (inflater.needsInput()) {
                    val peek = source.peek()
                    val read = try {
                        peek.readAtMostTo(input)
                    } finally {
                        peek.close()
                    }
                    if (read == -1) throw ZipArchiveException("Truncated raw-deflate ZIP entry")
                    inflater.setInput(input, 0, read)
                }

                val remainingBefore = inflater.remaining
                val produced = try {
                    inflater.inflate(output)
                } catch (error: DataFormatException) {
                    throw ZipArchiveException("Invalid raw-deflate ZIP entry", error)
                }
                val consumed = remainingBefore - inflater.remaining
                if (consumed > 0) {
                    source.skip(consumed.toLong())
                    inputByteCount += consumed
                }
                if (produced > 0) {
                    onUncompressedBytes(output, produced)
                    sink.write(output, 0, produced)
                    outputByteCount += produced
                } else if (inflater.needsDictionary()) {
                    throw ZipArchiveException("Raw-deflate ZIP entry requires a dictionary")
                } else if (!inflater.finished() && !inflater.needsInput() && consumed == 0) {
                    throw ZipArchiveException("Raw deflate decoder made no progress")
                }
            }
            return RawDeflateResult(inputByteCount, outputByteCount)
        } finally {
            inflater.end()
        }
    }
}
