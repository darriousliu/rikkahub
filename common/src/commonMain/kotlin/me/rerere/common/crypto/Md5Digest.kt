package me.rerere.common.crypto

fun interface Md5Digest {
    fun digest(data: ByteArray): ByteArray
}
