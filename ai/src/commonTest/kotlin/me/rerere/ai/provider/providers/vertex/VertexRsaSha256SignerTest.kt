package me.rerere.ai.provider.providers.vertex

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFails

class VertexRsaSha256SignerTest {
    @Test
    fun `platform PKCS8 signer matches the independent OpenSSL SHA256withRSA vector`() {
        val expected = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
            .decode(VertexTokenTestData.messageSignature)
        val signer = defaultVertexRsaSha256Signer()
        val pemVariants = listOf(
            VertexTokenTestData.privateKeyPem,
            VertexTokenTestData.privateKeyPem.replace("\n", "\r\n"),
        )
        for (pem in pemVariants) {
            assertContentEquals(expected, signer.signPkcs8Pem(pem, VertexTokenTestData.MESSAGE.encodeToByteArray()))
        }
    }

    @Test
    fun `platform signer rejects malformed PKCS8 instead of returning a signature`() {
        val signer = defaultVertexRsaSha256Signer()
        for (pem in listOf("not a PEM key", "-----BEGIN PRIVATE KEY-----\nAQID\n-----END PRIVATE KEY-----")) {
            assertFails { signer.signPkcs8Pem(pem, VertexTokenTestData.MESSAGE.encodeToByteArray()) }
        }
    }
}
