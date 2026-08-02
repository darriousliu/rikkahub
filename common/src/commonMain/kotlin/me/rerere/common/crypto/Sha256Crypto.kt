package me.rerere.common.crypto

fun interface Sha256Digest {
    fun digest(data: ByteArray): ByteArray
}

interface Sha256Crypto : Sha256Digest {
    fun hmac(key: ByteArray, data: ByteArray): ByteArray
}

interface IncrementalDigest {
    fun update(data: ByteArray, offset: Int = 0, length: Int = data.size - offset)

    fun digest(): ByteArray
}

expect object PlatformSha256Crypto : Sha256Crypto {
    override fun digest(data: ByteArray): ByteArray

    override fun hmac(key: ByteArray, data: ByteArray): ByteArray

    fun newDigest(): IncrementalDigest
}
