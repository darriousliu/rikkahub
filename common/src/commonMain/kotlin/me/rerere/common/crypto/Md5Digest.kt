package me.rerere.common.crypto

fun interface Md5Digest {
    fun digest(data: ByteArray): ByteArray
}

expect object PlatformMd5Digest : Md5Digest {
    override fun digest(data: ByteArray): ByteArray
}
