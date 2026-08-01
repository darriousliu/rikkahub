package me.rerere.asr.providers

import kotlinx.io.Buffer
import kotlinx.io.readByteArray

internal object VolcengineFrameCodec {
    fun buildFrame(
        messageType: Int,
        flags: Int,
        serialization: Int,
        compression: Int,
        payload: ByteArray,
    ): ByteArray = Buffer().run {
        writeByte(0x11.toByte())
        writeByte(((messageType shl 4) or (flags and 0x0F)).toByte())
        writeByte(((serialization shl 4) or (compression and 0x0F)).toByte())
        writeByte(0x00.toByte())
        writeInt(payload.size)
        write(payload)
        readByteArray()
    }

    fun parseResponse(data: ByteArray): ServerFrame? {
        if (data.size < HEADER_SIZE) return null

        val source = Buffer().apply { write(data) }
        source.readByte() // protocol version and header size
        val byte1 = source.readByte().toInt() and 0xFF
        val byte2 = source.readByte().toInt() and 0xFF
        source.readByte() // reserved

        val messageType = (byte1 shr 4) and 0x0F
        val messageFlags = byte1 and 0x0F
        val compression = byte2 and 0x0F

        return when (messageType) {
            MESSAGE_TYPE_RESULT -> {
                val hasSequence = (messageFlags and FLAG_HAS_SEQUENCE) != 0
                if (hasSequence) {
                    if (source.size < Int.SIZE_BYTES) return null
                    source.readInt()
                }

                if (source.size < Int.SIZE_BYTES) return null
                val payloadSize = source.readInt()

                if (payloadSize <= 0 || payloadSize.toLong() > source.size) return null
                ServerFrame.Result(
                    compression = compression,
                    payload = source.readByteArray(payloadSize),
                )
            }

            MESSAGE_TYPE_ERROR -> {
                if (source.size < Int.SIZE_BYTES) return null
                val code = source.readInt()

                if (source.size < Int.SIZE_BYTES) return null
                val messageSize = source.readInt()

                val message = if (messageSize > 0 && messageSize.toLong() <= source.size) {
                    source.readByteArray(messageSize)
                } else {
                    null
                }
                ServerFrame.Error(code = code, message = message)
            }

            else -> ServerFrame.Ignored(messageType)
        }
    }

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
