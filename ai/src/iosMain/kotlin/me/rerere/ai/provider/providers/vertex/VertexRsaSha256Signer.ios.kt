@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.io.encoding.ExperimentalEncodingApi::class,
)

package me.rerere.ai.provider.providers.vertex

import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import me.rerere.common.crypto.RsaSha256Signer
import kotlin.io.encoding.Base64
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.Foundation.CFBridgingRelease
import platform.Security.SecKeyCreateSignature
import platform.Security.SecKeyCreateWithData
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPrivate
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256

private object IosVertexRsaSha256Signer : RsaSha256Signer {
    override fun signPkcs8Pem(privateKeyPem: String, data: ByteArray): ByteArray {
        val keyBytes = decodePrivateKey(privateKeyPem)
        val keyData = keyBytes.toCFData()
        val attributes = CFDictionaryCreateMutable(null, 0, null, null)
            ?: error("Unable to create RSA key attributes")
        CFDictionarySetValue(attributes, kSecAttrKeyType, kSecAttrKeyTypeRSA)
        CFDictionarySetValue(attributes, kSecAttrKeyClass, kSecAttrKeyClassPrivate)

        val privateKey = SecKeyCreateWithData(keyData, attributes, null)
            ?: error("Unable to import PKCS#8 RSA private key")
        val messageData = data.toCFData()
        val signatureData = SecKeyCreateSignature(
            privateKey,
            kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256,
            messageData,
            null,
        ) ?: error("Unable to create RSA-SHA256 signature")

        return try {
            val length = CFDataGetLength(signatureData).toInt()
            val bytes = CFDataGetBytePtr(signatureData) ?: error("RSA signature has no bytes")
            ByteArray(length) { index -> bytes[index.toLong()].toByte() }
        } finally {
            CFBridgingRelease(signatureData)
            CFBridgingRelease(messageData)
            CFBridgingRelease(privateKey)
            CFBridgingRelease(attributes)
            CFBridgingRelease(keyData)
        }
    }
}

internal actual fun defaultVertexRsaSha256Signer(): RsaSha256Signer = IosVertexRsaSha256Signer

private fun ByteArray.toCFData() = usePinned { pinned ->
    CFDataCreate(
        allocator = null,
        bytes = pinned.addressOf(0).reinterpret<UByteVar>(),
        length = size.toLong(),
    ) ?: error("Unable to allocate CFData")
}

private fun decodePrivateKey(privateKeyPem: String): ByteArray {
    val der = Base64.Default.decode(
        privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .filterNot(Char::isWhitespace),
    )
    return if ("BEGIN RSA PRIVATE KEY" in privateKeyPem) der else extractPkcs1Key(der)
}

private fun extractPkcs1Key(pkcs8: ByteArray): ByteArray {
    val root = readDerValue(pkcs8, 0, expectedTag = 0x30)
    val version = readDerValue(pkcs8, root.contentOffset, expectedTag = 0x02)
    val algorithm = readDerValue(pkcs8, version.nextOffset, expectedTag = 0x30)
    val privateKey = readDerValue(pkcs8, algorithm.nextOffset, expectedTag = 0x04)
    require(privateKey.nextOffset <= root.nextOffset) { "Invalid PKCS#8 private key" }
    return pkcs8.copyOfRange(privateKey.contentOffset, privateKey.nextOffset)
}

private fun readDerValue(bytes: ByteArray, offset: Int, expectedTag: Int): DerValue {
    require(offset in bytes.indices && bytes[offset].toInt() and 0xff == expectedTag) {
        "Invalid DER tag at offset $offset"
    }
    require(offset + 1 < bytes.size) { "Truncated DER length" }
    val firstLength = bytes[offset + 1].toInt() and 0xff
    val (length, contentOffset) = if (firstLength and 0x80 == 0) {
        firstLength to (offset + 2)
    } else {
        val byteCount = firstLength and 0x7f
        require(byteCount in 1..4 && offset + 2 + byteCount <= bytes.size) { "Invalid DER length" }
        var value = 0
        repeat(byteCount) { index ->
            value = (value shl 8) or (bytes[offset + 2 + index].toInt() and 0xff)
        }
        value to (offset + 2 + byteCount)
    }
    require(length >= 0 && contentOffset + length <= bytes.size) { "Truncated DER value" }
    return DerValue(contentOffset = contentOffset, nextOffset = contentOffset + length)
}

private data class DerValue(
    val contentOffset: Int,
    val nextOffset: Int,
)
