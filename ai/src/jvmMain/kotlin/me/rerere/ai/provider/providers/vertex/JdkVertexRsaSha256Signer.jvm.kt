@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package me.rerere.ai.provider.providers.vertex

import me.rerere.common.crypto.RsaSha256Signer
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import kotlin.io.encoding.Base64

private object JvmVertexRsaSha256Signer : RsaSha256Signer {
    override fun signPkcs8Pem(privateKeyPem: String, data: ByteArray): ByteArray {
        val normalized = privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(
            PKCS8EncodedKeySpec(Base64.Default.decode(normalized)),
        )
        return Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(data)
            sign()
        }
    }
}

internal actual fun defaultVertexRsaSha256Signer(): RsaSha256Signer = JvmVertexRsaSha256Signer
