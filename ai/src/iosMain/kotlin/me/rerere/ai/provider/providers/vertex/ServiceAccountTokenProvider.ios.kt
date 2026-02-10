package me.rerere.ai.provider.providers.vertex

import io.github.vinceglb.filekit.utils.toByteArray
import io.github.vinceglb.filekit.utils.toNSData
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFErrorRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSDictionary
import platform.Foundation.create
import platform.Security.SecKeyCreateSignature
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyRef
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256

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
