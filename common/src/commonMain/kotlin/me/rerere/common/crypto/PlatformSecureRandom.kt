package me.rerere.common.crypto

expect object PlatformSecureRandom {
    fun nextBytes(size: Int): ByteArray
}
