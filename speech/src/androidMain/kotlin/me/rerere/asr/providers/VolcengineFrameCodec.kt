package me.rerere.asr.providers

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object VolcengineFrameCodec {
    fun buildFrame(
        messageType: Int,
        flags: Int,
        serialization: Int,
        compression: Int,
        payload: ByteArray,
    ): ByteArray {
        val header = byteArrayOf(
            0x11.toByte(),
            ((messageType shl 4) or (flags and 0x0F)).toByte(),
            ((serialization shl 4) or (compression and 0x0F)).toByte(),
            0x00,
        )
        val size = ByteBuffer.allocate(Int.SIZE_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(payload.size)
            .array()
        return header + size + payload
    }

    fun parseResponse(data: ByteArray): ServerFrame? {
        if (data.size < HEADER_SIZE) return null

        val byte1 = data[1].toInt() and 0xFF
        val byte2 = data[2].toInt() and 0xFF
        val messageType = (byte1 shr 4) and 0x0F
        val messageFlags = byte1 and 0x0F
        val compression = byte2 and 0x0F
        var offset = HEADER_SIZE

        return when (messageType) {
            MESSAGE_TYPE_RESULT -> {
                val hasSequence = (messageFlags and FLAG_HAS_SEQUENCE) != 0
                if (hasSequence) offset += Int.SIZE_BYTES

                if (offset + Int.SIZE_BYTES > data.size) return null
                val payloadSize = data.readBigEndianInt(offset)
                offset += Int.SIZE_BYTES

                if (payloadSize <= 0 || offset + payloadSize > data.size) return null
                ServerFrame.Result(
                    compression = compression,
                    payload = data.copyOfRange(offset, offset + payloadSize),
                )
            }

            MESSAGE_TYPE_ERROR -> {
                if (offset + Int.SIZE_BYTES > data.size) return null
                val code = data.readBigEndianInt(offset)
                offset += Int.SIZE_BYTES

                if (offset + Int.SIZE_BYTES > data.size) return null
                val messageSize = data.readBigEndianInt(offset)
                offset += Int.SIZE_BYTES

                val message = if (messageSize > 0 && offset + messageSize <= data.size) {
                    data.copyOfRange(offset, offset + messageSize)
                } else {
                    null
                }
                ServerFrame.Error(code = code, message = message)
            }

            else -> ServerFrame.Ignored(messageType)
        }
    }

    private fun ByteArray.readBigEndianInt(offset: Int): Int =
        ByteBuffer.wrap(this, offset, Int.SIZE_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .int

    internal sealed interface ServerFrame {
        data class Result(
            val compression: Int,
            val payload: ByteArray,
        ) : ServerFrame

        data class Error(
            val code: Int,
            val message: ByteArray?,
        ) : ServerFrame

        data class Ignored(val messageType: Int) : ServerFrame
    }

    private const val HEADER_SIZE = 4
    private const val MESSAGE_TYPE_RESULT = 0x09
    private const val MESSAGE_TYPE_ERROR = 0x0F
    private const val FLAG_HAS_SEQUENCE = 0x01
}
