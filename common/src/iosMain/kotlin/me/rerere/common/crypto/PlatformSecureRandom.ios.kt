@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package me.rerere.common.crypto

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

actual object PlatformSecureRandom {
    actual fun nextBytes(size: Int): ByteArray {
        require(size >= 0) { "size must be non-negative" }
        if (size == 0) return ByteArray(0)
        return ByteArray(size).also { bytes ->
            bytes.usePinned { pinned ->
                check(
                    SecRandomCopyBytes(
                        rnd = kSecRandomDefault,
                        count = size.convert(),
                        bytes = pinned.addressOf(0),
                    ) == errSecSuccess,
                ) { "SecRandomCopyBytes failed" }
            }
        }
    }
}
