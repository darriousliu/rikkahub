package me.rerere.rikkahub.data.sync.webdav

import co.touchlab.kermit.Logger
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.parser.Parser
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.sink
import io.github.vinceglb.filekit.size
import io.ktor.client.HttpClient
import io.ktor.client.request.basicAuth
import io.ktor.client.request.headers
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.utils.parseRFC1123DateTime
import me.rerere.rikkahub.utils.parseRFC850DateTime
import kotlin.time.Instant

private const val TAG = "WebDavClient"

class WebDavClient(
    private val config: WebDavConfig,
    private val httpClient: HttpClient,
) {
    private fun WebDavConfig.buildUrl(vararg segments: String): String {
        val base = url.trimEnd('/')
        val pathSegments = listOfNotNull(
            path.takeIf { it.isNotBlank() }?.trim('/'),
            *segments.map { it.trim('/') }.toTypedArray()
        ).filter { it.isNotEmpty() }

        return if (pathSegments.isEmpty()) {
            base
        } else {
            "$base/${pathSegments.joinToString("/")}"
        }
    }

    suspend fun put(
        path: String,
        data: ByteArray,
        contentType: String = "application/octet-stream",
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = config.buildUrl(path)
            Logger.d(TAG) { "PUT: $url" }

            val response: HttpResponse = httpClient.request(url) {
                method = HttpMethod.Put
                basicAuth(config.username, config.password)
                headers {
                    append("Content-Type", contentType)
                    append("Content-Length", data.size.toString())
                }
                setBody(data)
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                Logger.e(TAG) { "put failed: ${response.status} - $errorBody" }
                throw WebDavException("Failed to put: ${response.status}", response.status.value, errorBody)
            }

            Logger.d(TAG) { "put success: $path" }
        }
    }

    suspend fun put(
        path: String,
        file: PlatformFile,
        contentType: String = "application/octet-stream",
    ): Result<Unit> = withContext(Dispatchers.IO) {
        put(path, file.readBytes(), contentType)
    }

    suspend fun get(path: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val url = config.buildUrl(path)
            Logger.d(TAG) { "GET: $url" }

            val response: HttpResponse = httpClient.request(url) {
                method = HttpMethod.Get
                basicAuth(config.username, config.password)
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                Logger.e(TAG) { "get failed: ${response.status} - $errorBody" }
                throw WebDavException("Failed to get: ${response.status}", response.status.value, errorBody)
            }

            response.bodyAsBytes()
        }
    }

    suspend fun getStream(path: String): Result<ByteReadChannel> = withContext(Dispatchers.IO) {
        runCatching {
            val url = config.buildUrl(path)
            Logger.d(TAG) { "GET (stream): $url" }

            val response: HttpResponse = httpClient.request(url) {
                method = HttpMethod.Get
                basicAuth(config.username, config.password)
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                Logger.e(TAG) { "getStream failed: ${response.status} - $errorBody" }
                throw WebDavException("Failed to get stream: ${response.status}", response.status.value, errorBody)
            }

            response.bodyAsChannel()
        }
    }

    suspend fun downloadToFile(path: String, targetFile: PlatformFile): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = config.buildUrl(path)
            Logger.d(TAG) { "GET (download to file): $url" }

            httpClient.prepareRequest(url) {
                method = HttpMethod.Get
                basicAuth(config.username, config.password)
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val errorBody = response.bodyAsText()
                    Logger.e(TAG) { "downloadToFile failed: ${response.status} - $errorBody" }
                    throw WebDavException("Failed to download: ${response.status}", response.status.value, errorBody)
                }

                val channel = response.bodyAsChannel()
                targetFile.sink().buffered().use { outputStream ->
                    val buffer = ByteArray(8192)
                    while (!channel.isClosedForRead) {
                        val bytesRead = channel.readAvailable(buffer)
                        if (bytesRead > 0) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                    }
                }
                Logger.d(TAG) { "downloadToFile success: downloaded ${targetFile.size()} bytes" }
            }
        }
    }

    suspend fun delete(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = config.buildUrl(path)
            Logger.d(TAG) { "DELETE: $url" }

            val response: HttpResponse = httpClient.request(url) {
                method = HttpMethod.Delete
                basicAuth(config.username, config.password)
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                Logger.e(TAG) { "delete failed: ${response.status} - $errorBody" }
                throw WebDavException("Failed to delete: ${response.status}", response.status.value, errorBody)
            }

            Logger.d(TAG) { "delete success: $path" }
        }
    }

    suspend fun head(path: String): Result<WebDavResourceInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val url = config.buildUrl(path)
            Logger.d(TAG) { "HEAD: $url" }

            val response: HttpResponse = httpClient.request(url) {
                method = HttpMethod.Head
                basicAuth(config.username, config.password)
            }

            if (!response.status.isSuccess()) {
                throw WebDavException("Resource not found: ${response.status}", response.status.value, "")
            }

            WebDavResourceInfo(
                href = path,
                displayName = path.substringAfterLast("/"),
                contentLength = response.headers["Content-Length"]?.toLongOrNull() ?: 0,
                contentType = response.headers["Content-Type"] ?: "application/octet-stream",
                lastModified = parseLastModified(response.headers["Last-Modified"]),
                isCollection = false,
            )
        }
    }

    suspend fun mkcol(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = config.buildUrl(path)
            Logger.d(TAG) { "MKCOL: $url" }

            val response: HttpResponse = httpClient.request(url) {
                method = HttpMethod("MKCOL")
                basicAuth(config.username, config.password)
            }

            // 201 Created or 405 Method Not Allowed (already exists) are acceptable
            if (!response.status.isSuccess() && response.status != HttpStatusCode.MethodNotAllowed) {
                val errorBody = response.bodyAsText()
                Logger.e(TAG) { "mkcol failed: ${response.status} - $errorBody" }
                throw WebDavException(
                    "Failed to create collection: ${response.status}",
                    response.status.value,
                    errorBody
                )
            }

            Logger.d(TAG) { "mkcol success: $path" }
        }
    }

    suspend fun propfind(
        path: String = "",
        depth: Int = 1,
    ): Result<List<WebDavResourceInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = config.buildUrl(path)
            Logger.d(TAG) { "PROPFIND: $url, depth: $depth" }

            val propfindBody = """<?xml version="1.0" encoding="UTF-8"?>
                |<D:propfind xmlns:D="DAV:">
                |  <D:prop>
                |    <D:displayname/>
                |    <D:getcontentlength/>
                |    <D:getcontenttype/>
                |    <D:getlastmodified/>
                |    <D:resourcetype/>
                |  </D:prop>
                |</D:propfind>
            """.trimMargin()

            val response: HttpResponse = httpClient.request(url) {
                method = HttpMethod("PROPFIND")
                basicAuth(config.username, config.password)
                headers {
                    append("Content-Type", "application/xml; charset=utf-8")
                    append("Depth", depth.toString())
                }
                setBody(propfindBody)
            }

            if (!response.status.isSuccess() && response.status.value != 207) {
                val errorBody = response.bodyAsText()
                Logger.e(TAG) { "propfind failed: ${response.status} - $errorBody" }
                throw WebDavException("Failed to propfind: ${response.status}", response.status.value, errorBody)
            }

            val xmlBody = response.bodyAsText()
            parsePropfindResponse(xmlBody, url)
        }
    }

    suspend fun exists(path: String): Boolean {
        return head(path).isSuccess
    }

    suspend fun ensureCollectionExists(path: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val targetUrl = config.buildUrl(path)
            Logger.d(TAG) { "Ensuring collection exists: $targetUrl" }

            // Try propfind first to check if it exists
            val propfindResult = propfind(path, depth = 0)
            if (propfindResult.isSuccess) {
                Logger.d(TAG) { "Collection already exists: $targetUrl" }
                return@runCatching
            }

            // Create collection if not exists
            mkcol(path).getOrThrow()
        }
    }

    suspend fun list(path: String = ""): Result<List<WebDavResourceInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val result = propfind(path, depth = 1).getOrThrow()
            // Filter out the parent directory itself (first entry)
            if (result.isNotEmpty()) {
                result.drop(1)
            } else {
                emptyList()
            }
        }
    }

    private fun parsePropfindResponse(xml: String, baseUrl: String): List<WebDavResourceInfo> {
        // Ksoup 用 XML parser 模式解析
        val doc = Ksoup.parse(xml, parser = Parser.xmlParser())
        // 选取所有 <D:response> 或 <response>（WebDAV 命名空间前缀不固定）
        val responseElements = doc.select("response")
        return responseElements.mapNotNull { response ->
            val href = response.select("href").firstOrNull()?.text()?.trim()
                ?: return@mapNotNull null
            val displayName = response.select("displayname").firstOrNull()?.text()?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: href.trimEnd('/').substringAfterLast("/")
            val contentLength = response.select("getcontentlength").firstOrNull()
                ?.text()?.trim()?.toLongOrNull() ?: 0L
            val contentType = response.select("getcontenttype").firstOrNull()
                ?.text()?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "application/octet-stream"
            val lastModified = response.select("getlastmodified").firstOrNull()
                ?.text()?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { parseLastModified(it) }
            // <resourcetype> 下存在 <collection> 子元素则为目录
            val isCollection = response.select("resourcetype").firstOrNull()
                ?.select("collection")
                ?.isNotEmpty() == true
            WebDavResourceInfo(
                href = href,
                displayName = displayName,
                contentLength = contentLength,
                contentType = contentType,
                lastModified = lastModified,
                isCollection = isCollection,
            )
        }
    }

    private fun parseLastModified(dateString: String?): Instant? {
        if (dateString.isNullOrBlank()) return null

        return try {
            // RFC 1123 format: "Tue, 15 Nov 1994 08:12:31 GMT"
            parseRFC1123DateTime(dateString)
        } catch (e: Exception) {
            try {
                // RFC 850 format: "Tuesday, 15-Nov-94 08:12:31 GMT"
                parseRFC850DateTime(dateString)
            } catch (e: Exception) {
                try {
                    // ISO 8601
                    Instant.parse(dateString)
                } catch (e: Exception) {
                    Logger.w(TAG) { "Failed to parse date: $dateString" }
                    null
                }
            }
        }
    }
}

data class WebDavResourceInfo(
    val href: String,
    val displayName: String,
    val contentLength: Long,
    val contentType: String,
    val lastModified: Instant?,
    val isCollection: Boolean,
)

class WebDavException(
    message: String,
    val statusCode: Int,
    val responseBody: String,
) : Exception(message)
