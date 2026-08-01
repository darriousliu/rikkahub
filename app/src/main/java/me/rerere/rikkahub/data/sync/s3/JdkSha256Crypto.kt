package me.rerere.rikkahub.data.sync.s3

import me.rerere.common.crypto.Sha256Crypto
import java.io.File
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object JdkSha256Crypto : Sha256Crypto {
    override fun digest(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    override fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    fun digestHex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val byteCount = input.read(buffer)
                if (byteCount <= 0) break
                digest.update(buffer, 0, byteCount)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
