package me.rerere.common.crypto

fun interface Sha256Digest {
    fun digest(data: ByteArray): ByteArray
}

interface Sha256Crypto : Sha256Digest {
    fun hmac(key: ByteArray, data: ByteArray): ByteArray
}
