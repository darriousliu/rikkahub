package me.rerere.common.archive

import kotlinx.io.Sink
import kotlinx.io.Source

internal data class RawDeflateResult(
    val inputByteCount: Long,
    val outputByteCount: Long,
)

internal interface RawDeflateCodec {
    fun deflate(
        source: Source,
        sink: Sink,
        onUncompressedBytes: (ByteArray, Int) -> Unit,
    ): RawDeflateResult

    /** Inflates one raw-deflate stream and leaves any following bytes unread in [source]. */
    fun inflate(
        source: Source,
        sink: Sink,
        onUncompressedBytes: (ByteArray, Int) -> Unit,
    ): RawDeflateResult
}
