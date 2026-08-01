package me.rerere.common.crypto

fun interface RsaSha256Signer {
    fun signPkcs8Pem(privateKeyPem: String, data: ByteArray): ByteArray
}
