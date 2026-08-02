@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package me.rerere.common.crypto

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.CC_MD5
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256_Final
import platform.CoreCrypto.CC_SHA256_Init
import platform.CoreCrypto.CC_SHA256_Update
import platform.CoreCrypto.CC_SHA256state_st
import platform.CoreCrypto.kCCHmacAlgSHA256

actual object PlatformSha256Crypto : Sha256Crypto {
    actual override fun digest(data: ByteArray): ByteArray =
        newDigest().also { it.update(data) }.digest()

    actual override fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val output = UByteArray(CC_SHA256_DIGEST_LENGTH)
        val pinnedKeyBytes = key.nonEmptyForPinning()
        val pinnedDataBytes = data.nonEmptyForPinning()
        pinnedKeyBytes.usePinned { pinnedKey ->
            pinnedDataBytes.usePinned { pinnedData ->
                output.usePinned { pinnedOutput ->
                    CCHmac(
                        algorithm = kCCHmacAlgSHA256,
                        key = pinnedKey.addressOf(0),
                        keyLength = key.size.convert(),
                        data = pinnedData.addressOf(0),
                        dataLength = data.size.convert(),
                        macOut = pinnedOutput.addressOf(0),
                    )
                }
            }
        }
        return output.toByteArray()
    }

    actual fun newDigest(): IncrementalDigest = IosSha256Digest()
}

actual object PlatformMd5Digest : Md5Digest {
    actual override fun digest(data: ByteArray): ByteArray {
        val output = UByteArray(CC_MD5_DIGEST_LENGTH)
        data.nonEmptyForPinning().usePinned { pinnedData ->
            output.usePinned { pinnedOutput ->
                CC_MD5(pinnedData.addressOf(0), data.size.convert(), pinnedOutput.addressOf(0))
            }
        }
        return output.toByteArray()
    }
}

private fun ByteArray.nonEmptyForPinning(): ByteArray = if (isEmpty()) byteArrayOf(0) else this

private class IosSha256Digest : IncrementalDigest {
    private val context = kotlinx.cinterop.nativeHeap.alloc<CC_SHA256state_st>()
    private var finished = false

    init {
        check(CC_SHA256_Init(context.ptr) == 1)
    }

    override fun update(data: ByteArray, offset: Int, length: Int) {
        check(!finished) { "Digest has already been finalized" }
        require(offset >= 0 && length >= 0 && offset + length <= data.size)
        if (length == 0) return
        data.usePinned { pinned ->
            check(CC_SHA256_Update(context.ptr, pinned.addressOf(offset), length.convert()) == 1)
        }
    }

    override fun digest(): ByteArray {
        check(!finished) { "Digest has already been finalized" }
        finished = true
        val output = UByteArray(CC_SHA256_DIGEST_LENGTH)
        output.usePinned { pinned ->
            check(CC_SHA256_Final(pinned.addressOf(0), context.ptr) == 1)
        }
        kotlinx.cinterop.nativeHeap.free(context.rawPtr)
        return output.toByteArray()
    }
}
