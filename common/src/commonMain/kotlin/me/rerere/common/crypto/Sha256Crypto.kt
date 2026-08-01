package me.rerere.common.crypto

interface Sha256Crypto {
    fun digest(data: ByteArray): ByteArray

    fun hmac(key: ByteArray, data: ByteArray): ByteArray
}
