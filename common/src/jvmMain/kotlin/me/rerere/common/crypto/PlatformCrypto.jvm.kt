package me.rerere.common.crypto

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

actual object PlatformSha256Crypto : Sha256Crypto {
    actual override fun digest(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    actual override fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    actual fun newDigest(): IncrementalDigest = JdkIncrementalDigest("SHA-256")
}

actual object PlatformMd5Digest : Md5Digest {
    actual override fun digest(data: ByteArray): ByteArray =
        MessageDigest.getInstance("MD5").digest(data)
}

private class JdkIncrementalDigest(algorithm: String) : IncrementalDigest {
    private val delegate = MessageDigest.getInstance(algorithm)

    override fun update(data: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset + length <= data.size)
        delegate.update(data, offset, length)
    }

    override fun digest(): ByteArray = delegate.digest()
}
