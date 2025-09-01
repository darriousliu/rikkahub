package me.rerere.ai.provider.providers.vertex

import kotlinx.cinterop.*
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFErrorRefVar
import platform.Foundation.*
import platform.Security.*
import platform.posix.memcpy

actual interface PrivateKey {
    val secKey: SecKeyRef
}

class PrivateKeyImpl(
    override val secKey: SecKeyRef
) : PrivateKey

actual fun parsePkcs8PrivateKey(pem: String): PrivateKey {
    val normalized = pem
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replace("\\s".toRegex(), "")

    val nsData = NSData.create(base64Encoding = normalized)
        ?: throw IllegalArgumentException("Invalid PEM format")
    val nsDataRef: CFDataRef? = CFBridgingRetain(nsData)?.reinterpret()

    val attributes = NSDictionary.create(
        mapOf(
            kSecAttrKeyType to kSecAttrKeyTypeRSA,
            kSecAttrKeyClass to kSecAttrKeyClassPublic
        )
    )
    val attributeRef: CFDictionaryRef? = CFBridgingRetain(attributes)?.reinterpret()

    memScoped {
        val errorPtr = alloc<CFErrorRefVar>()
        val secKey = SecKeyCreateWithData(nsDataRef, attributeRef, errorPtr.ptr)
            ?: throw IllegalArgumentException("Failed to create private key: ${errorPtr.value}")

        return PrivateKeyImpl(secKey)
    }
}

actual fun signRs256(data: ByteArray, privateKey: PrivateKey): ByteArray {
    val nsData = data.toNSData()
    val nsDataRef: CFDataRef? = CFBridgingRetain(nsData)?.reinterpret()

    memScoped {
        val errorPtr = alloc<CFErrorRefVar>()
        val signature = SecKeyCreateSignature(
            privateKey.secKey,
            kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256,
            nsDataRef,
            errorPtr.ptr
        ) ?: throw IllegalStateException("Failed to sign data: ${errorPtr.value}")
        val signatureData = CFBridgingRelease(signature) as NSData

        return signatureData.toByteArray()
    }
}

private fun ByteArray.toNSData(): NSData = memScoped {
    NSData.create(bytes = allocArrayOf(this@toNSData), length = size.convert())
}

private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).apply {
    usePinned {
        memcpy(it.addressOf(0), bytes, length)
    }
}
