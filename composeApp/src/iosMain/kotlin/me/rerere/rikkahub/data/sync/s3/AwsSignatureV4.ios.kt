package me.rerere.rikkahub.data.sync.s3

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.kCCHmacAlgSHA256

@OptIn(ExperimentalForeignApi::class)
internal actual fun sha256(data: ByteArray): ByteArray {
    val result = ByteArray(CC_SHA256_DIGEST_LENGTH)
    data.usePinned { pinnedData ->
        result.usePinned { pinnedResult ->
            CC_SHA256(
                pinnedData.addressOf(0),
                data.size.toUInt(),
                pinnedResult.addressOf(0).reinterpret()
            )
        }
    }
    return result
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val result = ByteArray(CC_SHA256_DIGEST_LENGTH)
    key.usePinned { pinnedKey ->
        data.usePinned { pinnedData ->
            result.usePinned { pinnedResult ->
                CCHmac(
                    kCCHmacAlgSHA256,
                    pinnedKey.addressOf(0),
                    key.size.toULong(),
                    pinnedData.addressOf(0),
                    data.size.toULong(),
                    pinnedResult.addressOf(0)
                )
            }
        }
    }
    return result
}
