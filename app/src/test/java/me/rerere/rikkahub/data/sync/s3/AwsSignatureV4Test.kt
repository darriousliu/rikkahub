package me.rerere.rikkahub.data.sync.s3

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Instant

class AwsSignatureV4Test {
    @Test
    fun `matches the aws s3 get object signature vector`() {
        val signed = AwsSignatureV4.sign(
            config = S3Config(
                endpoint = "https://s3.amazonaws.com",
                accessKeyId = "AKIAIOSFODNN7EXAMPLE",
                secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                bucket = "examplebucket",
                region = "us-east-1",
                pathStyle = false,
            ),
            method = "GET",
            path = "/test.txt",
            headers = mapOf("range" to "bytes=0-9"),
            payloadHash = EMPTY_SHA256,
            now = Instant.parse("2013-05-24T00:00:00Z"),
        )

        assertEquals("https://examplebucket.s3.amazonaws.com/test.txt", signed.url)
        assertEquals("examplebucket.s3.amazonaws.com", signed.headers["host"])
        assertEquals("20130524T000000Z", signed.headers["x-amz-date"])
        assertEquals(EMPTY_SHA256, signed.headers["x-amz-content-sha256"])
        assertEquals(
            "AWS4-HMAC-SHA256 " +
                "Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request, " +
                "SignedHeaders=host;range;x-amz-content-sha256;x-amz-date, " +
                "Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41",
            signed.headers["authorization"],
        )
    }

    private companion object {
        const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }
}
