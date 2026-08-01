package me.rerere.tts

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
internal fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.dataWithBytes(
            bytes = pinned.addressOf(0),
            length = size.toULong(),
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    if (length == 0UL) return ByteArray(0)
    val source = bytes ?: return ByteArray(0)
    return ByteArray(length.toInt()).also { output ->
        output.usePinned { pinned ->
            memcpy(pinned.addressOf(0), source, length)
        }
    }
}
