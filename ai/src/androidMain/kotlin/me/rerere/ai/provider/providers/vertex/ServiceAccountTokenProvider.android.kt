package me.rerere.ai.provider.providers.vertex

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.security.PrivateKey as JavaPrivateKey

actual typealias PrivateKey = JavaPrivateKey

actual fun parsePkcs8PrivateKey(pem: String): PrivateKey {
    val normalized = pem
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replace("\\s".toRegex(), "")
    val der = Base64.getDecoder().decode(normalized)
    val keySpec = PKCS8EncodedKeySpec(der)
    return KeyFactory.getInstance("RSA").generatePrivate(keySpec)
}

actual fun signRs256(data: ByteArray, privateKey: PrivateKey): ByteArray {
    val sig = Signature.getInstance("SHA256withRSA")
    sig.initSign(privateKey)
    sig.update(data)
    return sig.sign()
}
