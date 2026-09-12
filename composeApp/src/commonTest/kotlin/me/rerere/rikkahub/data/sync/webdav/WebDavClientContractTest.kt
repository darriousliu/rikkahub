package me.rerere.rikkahub.data.sync.webdav

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import me.rerere.rikkahub.data.datastore.WebDavConfig
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WebDavClientContractTest {
    @Test
    fun `each operation retains its original HTTP failure message and body`() = runTest {
        val status = HttpStatusCode.InternalServerError
        val http = HttpClient(MockEngine { respond("server error", status) })
        val client = WebDavClient(WebDavConfig(url = "https://backup.invalid/dav", path = "folder", username = "user", password = "pass"), http)
        try {
            val messages = mapOf(
                "put" to "Failed to put", "stream" to "Failed to put file", "get" to "Failed to get",
                "head" to "Resource not found", "delete" to "Failed to delete", "download" to "Failed to download",
                "list" to "Failed to propfind", "mkcol" to "Failed to create collection",
            )
            for ((operation, prefix) in messages) {
                val result = when (operation) {
                    "put" -> client.put("backup.zip", byteArrayOf(1))
                    "stream" -> client.put("backup.zip", 1L, { ByteReadChannel(byteArrayOf(1)) })
                    "get" -> client.get("backup.zip")
                    "head" -> client.head("backup.zip")
                    "delete" -> client.delete("backup.zip")
                    "download" -> client.download("backup.zip") { _, _ -> }
                    "list" -> client.propfind()
                    else -> client.mkcol("folder")
                }
                val error = assertIs<WebDavException>(result.exceptionOrNull())
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
        val client = WebDavClient(WebDavConfig(url = "https://backup.invalid/dav", path = "folder", username = "user", password = "pass"), http)
        try {
            assertEquals(Unit, client.put("data.bin", bytes, "application/zip").getOrThrow())
            assertEquals(Unit, client.put("data.bin", bytes.size.toLong(), { ByteReadChannel(bytes) }, "application/zip").getOrThrow())
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
        val client = WebDavClient(WebDavConfig(url = "https://backup.invalid/dav", path = "folder", username = "user", password = "pass"), http)
        try {
            assertTrue(client.put("backup.zip", byteArrayOf(1), "invalid").isFailure)
            assertTrue(client.put("backup.zip", 1L, { ByteReadChannel(byteArrayOf(1)) }, "invalid").isFailure)
            assertEquals(false, sent)
        } finally {
            http.close()
        }
    }
}
