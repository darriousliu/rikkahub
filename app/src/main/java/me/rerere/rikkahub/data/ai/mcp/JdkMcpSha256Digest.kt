package me.rerere.rikkahub.data.ai.mcp

import me.rerere.common.crypto.Sha256Digest
import java.security.MessageDigest

internal object JdkMcpSha256Digest : Sha256Digest {
    override fun digest(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }
}
