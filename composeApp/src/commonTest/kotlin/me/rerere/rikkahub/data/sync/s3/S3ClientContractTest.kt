package me.rerere.rikkahub.data.sync.s3

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import me.rerere.common.crypto.PlatformSha256Crypto
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class S3ClientContractTest {
    @Test
    fun `each operation retains its original HTTP failure message and body`() = runTest {
        val status = HttpStatusCode.InternalServerError
        val http = HttpClient(MockEngine { respond("server error", status) })
        val client = S3Client(S3Config(endpoint = "https://backup.invalid", bucket = "bucket", accessKeyId = "key", secretAccessKey = "secret"), http, PlatformSha256Crypto)
        try {
            val messages = mapOf(
                "put" to "Failed to put object", "stream" to "Failed to put object", "get" to "Failed to get object",
            "head" to "Object not found", "delete" to "Failed to delete object", "download" to "Failed to download object",
            "list" to "Failed to list objects",
            )
            for ((operation, prefix) in messages) {
                val result = when (operation) {
                    "put" -> client.putObject("backup.zip", byteArrayOf(1))
                    "stream" -> client.putObject("backup.zip", 1L, "hash", { ByteReadChannel(byteArrayOf(1)) })
                    "get" -> client.getObject("backup.zip")
                    "head" -> client.headObject("backup.zip")
                    "delete" -> client.deleteObject("backup.zip")
                    "download" -> client.downloadObject("backup.zip") { _, _ -> }
                    else -> client.listObjects()
                }
                val error = assertIs<S3Exception>(result.exceptionOrNull())
                assertEquals("$prefix: $status", error.message)
                assertEquals(if (operation == "head") "" else "server error", error.responseBody)
            }
        } finally {
            http.close()
        }
    }

    @Test
    fun `byte and channel uploads retain content headers bytes and original result contract`() = runTest {
        val bytes = ByteArray(16387) { (it % 251).toByte() }
        val requests = mutableListOf<HttpRequestData>()
        val http = HttpClient(MockEngine { request ->
            requests += request
            assertContentEquals(bytes, request.body.toByteArray())
            assertEquals(bytes.size.toLong(), request.body.contentLength)
            assertEquals("application/zip", request.body.contentType.toString())
            respond("", HttpStatusCode.Created)
        })
        val client = S3Client(S3Config(endpoint = "https://backup.invalid", bucket = "bucket", accessKeyId = "key", secretAccessKey = "secret"), http, PlatformSha256Crypto)
        try {
            assertEquals(Unit, client.putObject("data.bin", bytes, "application/zip").getOrThrow())
            assertEquals(Unit, client.putObject("data.bin", bytes.size.toLong(), "test-hash", { ByteReadChannel(bytes) }, "application/zip").getOrThrow())
            assertEquals(listOf("PUT", "PUT"), requests.map { it.method.value })
            assertTrue(requests.all { it.headers["Authorization"] != null })
        } finally {
            http.close()
        }
    }

    @Test
    fun `invalid upload content type returns failure without sending a request`() = runTest {
        var sent = false
        val http = HttpClient(MockEngine { sent = true; respond("") })
        val client = S3Client(S3Config(endpoint = "https://backup.invalid", bucket = "bucket", accessKeyId = "key", secretAccessKey = "secret"), http, PlatformSha256Crypto)
        try {
            assertTrue(client.putObject("backup.zip", byteArrayOf(1), "invalid").isFailure)
            assertTrue(client.putObject("backup.zip", 1L, "hash", { ByteReadChannel(byteArrayOf(1)) }, "invalid").isFailure)
            assertEquals(false, sent)
        } finally {
            http.close()
        }
    }
}
