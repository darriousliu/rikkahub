package me.rerere.common.crypto

import java.security.SecureRandom

actual object PlatformSecureRandom {
    private val delegate = SecureRandom()

    actual fun nextBytes(size: Int): ByteArray {
        require(size >= 0) { "size must be non-negative" }
        return ByteArray(size).also(delegate::nextBytes)
    }
}
