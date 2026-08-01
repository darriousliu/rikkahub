package me.rerere.rikkahub.data.sync.importer

import me.rerere.common.crypto.Md5Digest
import java.security.MessageDigest

internal object JdkChatboxMd5Digest : Md5Digest {
    override fun digest(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("MD5").digest(data)
    }
}
