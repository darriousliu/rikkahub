package me.rerere.common.archive

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.io.Sink
import kotlinx.io.Source
import platform.zlib.MAX_WBITS
import platform.zlib.Z_BUF_ERROR
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_NEED_DICT
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.deflate
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.z_stream

actual object PlatformZipArchive : ZipArchive by CommonZipArchive(IosRawDeflateCodec)

@OptIn(ExperimentalForeignApi::class)
private object IosRawDeflateCodec : RawDeflateCodec {
    override fun deflate(
        source: Source,
        sink: Sink,
        onUncompressedBytes: (ByteArray, Int) -> Unit,
    ): RawDeflateResult = memScoped {
        val stream = alloc<z_stream>().apply {
            zalloc = null
            zfree = null
            opaque = null
            next_in = null
            avail_in = 0u
        }
        val initStatus = deflateInit2(
            stream.ptr,
            Z_DEFAULT_COMPRESSION,
            Z_DEFLATED,
            -MAX_WBITS,
            ZLIB_MEMORY_LEVEL,
            Z_DEFAULT_STRATEGY,
        )
        if (initStatus != Z_OK) throw ZipArchiveException("Unable to initialize iOS raw deflate: $initStatus")

        val input = ByteArray(IO_BUFFER_SIZE)
        val output = ByteArray(IO_BUFFER_SIZE)
        var inputByteCount = 0L
        var outputByteCount = 0L
        try {
            while (true) {
                val read = source.readAtMostTo(input)
                if (read == -1) break
                onUncompressedBytes(input, read)
                inputByteCount += read
                input.usePinned { pinnedInput ->
                    stream.next_in = pinnedInput.addressOf(0).reinterpret()
                    stream.avail_in = read.toUInt()
                    while (stream.avail_in > 0u) {
                        val availableBefore = stream.avail_in
                        val produced = output.usePinned { pinnedOutput ->
                            stream.next_out = pinnedOutput.addressOf(0).reinterpret()
                            stream.avail_out = output.size.toUInt()
                            val status = deflate(stream.ptr, Z_NO_FLUSH)
                            if (status != Z_OK) throw zlibError("deflate", status, stream.msg?.toKString())
                            output.size - stream.avail_out.toInt()
                        }
                        val consumed = availableBefore - stream.avail_in
                        if (produced > 0) {
                            sink.write(output, 0, produced)
                            outputByteCount += produced
                        } else if (consumed == 0u) {
                            throw ZipArchiveException("iOS raw deflate encoder made no progress")
                        }
                    }
                    stream.next_in = null
                }
            }

            var finished = false
            while (!finished) {
                val produced = output.usePinned { pinnedOutput ->
                    stream.next_out = pinnedOutput.addressOf(0).reinterpret()
                    stream.avail_out = output.size.toUInt()
                    val status = deflate(stream.ptr, Z_FINISH)
                    when (status) {
                        Z_OK -> Unit
                        Z_STREAM_END -> finished = true
                        else -> throw zlibError("deflate finish", status, stream.msg?.toKString())
                    }
                    output.size - stream.avail_out.toInt()
                }
                if (produced > 0) {
                    sink.write(output, 0, produced)
                    outputByteCount += produced
                } else if (!finished) {
                    throw ZipArchiveException("iOS raw deflate encoder did not finish")
                }
            }
            RawDeflateResult(inputByteCount, outputByteCount)
        } finally {
            deflateEnd(stream.ptr)
        }
    }

    override fun inflate(
        source: Source,
        sink: Sink,
        onUncompressedBytes: (ByteArray, Int) -> Unit,
    ): RawDeflateResult = memScoped {
        val stream = alloc<z_stream>().apply {
            zalloc = null
            zfree = null
            opaque = null
            next_in = null
            avail_in = 0u
        }
        val initStatus = inflateInit2(stream.ptr, -MAX_WBITS)
        if (initStatus != Z_OK) throw ZipArchiveException("Unable to initialize iOS raw inflate: $initStatus")

        val input = ByteArray(IO_BUFFER_SIZE)
        val output = ByteArray(IO_BUFFER_SIZE)
        var inputByteCount = 0L
        var outputByteCount = 0L
        var finished = false
        try {
            while (!finished) {
                val peek = source.peek()
                val read = try {
                    peek.readAtMostTo(input)
                } finally {
                    peek.close()
                }
                if (read == -1) throw ZipArchiveException("Truncated raw-deflate ZIP entry")

                input.usePinned { pinnedInput ->
                    stream.next_in = pinnedInput.addressOf(0).reinterpret()
                    stream.avail_in = read.toUInt()
                    while (stream.avail_in > 0u && !finished) {
                        val availableBefore = stream.avail_in
                        var status = Z_OK
                        val produced = output.usePinned { pinnedOutput ->
                            stream.next_out = pinnedOutput.addressOf(0).reinterpret()
                            stream.avail_out = output.size.toUInt()
                            status = inflate(stream.ptr, Z_NO_FLUSH)
                            output.size - stream.avail_out.toInt()
                        }
                        val consumed = (availableBefore - stream.avail_in).toInt()
                        if (consumed > 0) {
                            source.skip(consumed.toLong())
                            inputByteCount += consumed
                        }
                        if (produced > 0) {
                            onUncompressedBytes(output, produced)
                            sink.write(output, 0, produced)
                            outputByteCount += produced
                        }

                        when (status) {
                            Z_STREAM_END -> finished = true
                            Z_OK, Z_BUF_ERROR -> if (consumed == 0 && produced == 0) {
                                throw ZipArchiveException("iOS raw deflate decoder made no progress")
                            }

                            Z_NEED_DICT -> throw ZipArchiveException("Raw-deflate ZIP entry requires a dictionary")
                            else -> throw zlibError("inflate", status, stream.msg?.toKString())
                        }
                    }
                    stream.next_in = null
                }
            }
            RawDeflateResult(inputByteCount, outputByteCount)
        } finally {
            inflateEnd(stream.ptr)
        }
    }

    private fun zlibError(operation: String, status: Int, message: String?): ZipArchiveException {
        val detail = message?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
        return ZipArchiveException("iOS zlib $operation failed with status $status$detail")
    }

    private const val IO_BUFFER_SIZE = 8 * 1024
    private const val ZLIB_MEMORY_LEVEL = 8
}
