package me.rerere.rikkahub.data.sync.s3

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import me.rerere.common.crypto.Sha256Crypto
import me.rerere.common.logging.RikkaLog as Log
import kotlin.time.Instant

private const val TAG = "S3Client"

class S3Client(
    private val config: S3Config,
    private val httpClient: HttpClient,
    private val crypto: Sha256Crypto,
) {
    suspend fun putObject(
        key: String,
        data: ByteArray,
        contentType: String = "application/octet-stream",
    ): Result<Unit> = runCatching {
        val path = "/${key.trimStart('/')}"
        val signed = AwsSignatureV4.sign(
            config = config,
            method = "PUT",
            path = path,
            payload = data,
            contentType = contentType,
            crypto = crypto,
        )
        val parsedContentType = ContentType.parse(contentType)
        executeUnitRequest(
            signed = signed,
            requestMethod = HttpMethod.Put,
            body = object : OutgoingContent.ByteArrayContent() {
                override val contentLength = data.size.toLong()
                override val contentType = parsedContentType
                override fun bytes(): ByteArray = data
            },
            operation = "putObject",
            key = key,
        )
    }

    suspend fun putObject(
        key: String,
        contentLength: Long,
        payloadHash: String,
        content: () -> ByteReadChannel,
        contentType: String = "application/octet-stream",
    ): Result<Unit> = runCatching {
        val path = "/${key.trimStart('/')}"
        val signed = AwsSignatureV4.sign(
            config = config,
            method = "PUT",
            path = path,
            payloadHash = payloadHash,
            contentLength = contentLength,
            contentType = contentType,
            crypto = crypto,
        )
        val parsedContentType = ContentType.parse(contentType)
        val resolvedContentLength = contentLength
        executeUnitRequest(
            signed = signed,
            requestMethod = HttpMethod.Put,
            body = object : OutgoingContent.ReadChannelContent() {
                override val contentLength = resolvedContentLength
                override val contentType = parsedContentType
                override fun readFrom(): ByteReadChannel = content()
            },
            operation = "putObject",
            key = key,
        )
    }

    suspend fun getObject(key: String): Result<ByteArray> = runCatching {
        val response = executeObjectRequest(key, HttpMethod.Get)
        response.body<ByteArray>()
    }

    suspend fun downloadObject(
        key: String,
        writeChunk: suspend (buffer: ByteArray, byteCount: Int) -> Unit,
    ): Result<Long> = runCatching {
        val path = "/${key.trimStart('/')}"
        val signed = AwsSignatureV4.sign(
            config = config,
            method = "GET",
            path = path,
            crypto = crypto,
        )
        var downloaded = 0L
        httpClient.prepareRequest(signed.url) {
            method = HttpMethod.Get
            headers { signed.headers.forEach { (name, value) -> append(name, value) } }
        }.execute { response ->
            response.requireSuccess("download object")
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(8192)
            while (true) {
                val byteCount = channel.readAvailable(buffer)
                if (byteCount < 0) break
                if (byteCount > 0) {
                    writeChunk(buffer, byteCount)
                    downloaded += byteCount
                }
            }
        }
        Log.d(TAG, "downloadObject success: $key ($downloaded bytes)")
        downloaded
    }

    suspend fun deleteObject(key: String): Result<Unit> = runCatching {
        executeObjectRequest(key, HttpMethod.Delete)
        Log.d(TAG, "deleteObject success: $key")
    }

    suspend fun headObject(key: String): Result<S3ObjectMetadata> = runCatching {
        val response = executeObjectRequest(key, HttpMethod.Head)
        S3ObjectMetadata(
            key = key,
            size = response.headers["content-length"]?.toLongOrNull() ?: 0,
            contentType = response.headers["content-type"] ?: "application/octet-stream",
            etag = response.headers["etag"]?.trim('"'),
            lastModified = response.headers["last-modified"],
        )
    }

    suspend fun listObjects(
        prefix: String = "",
        delimiter: String = "",
        maxKeys: Int = 1000,
        continuationToken: String? = null,
    ): Result<S3ListResult> = runCatching {
        val queryParams = mutableMapOf(
            "list-type" to "2",
            "max-keys" to maxKeys.toString(),
        )
        if (prefix.isNotEmpty()) queryParams["prefix"] = prefix
        if (delimiter.isNotEmpty()) queryParams["delimiter"] = delimiter
        continuationToken?.let { queryParams["continuation-token"] = it }
        val signed = AwsSignatureV4.sign(
            config = config,
            method = "GET",
            path = "/",
            queryParams = queryParams,
            crypto = crypto,
        )
        val response = httpClient.request(signed.url) {
            method = HttpMethod.Get
            headers { signed.headers.forEach { (name, value) -> append(name, value) } }
        }
        response.requireSuccess("list objects")
        parseListObjectsResponse(response.bodyAsText())
    }

    suspend fun objectExists(key: String): Boolean = headObject(key).isSuccess

    fun getPublicUrl(key: String): String {
        val path = "/${key.trimStart('/')}"
        return if (config.pathStyle) {
            "${config.endpoint.trimEnd('/')}/${config.bucket}$path"
        } else {
            val scheme = if (config.isHttps) "https://" else "http://"
            "$scheme${config.bucket}.${config.host}$path"
        }
    }

    private suspend fun executeUnitRequest(
        signed: AwsSignatureV4.SignedRequest,
        requestMethod: HttpMethod,
        body: OutgoingContent,
        operation: String,
        key: String,
    ) {
        val response = httpClient.request(signed.url) {
            method = requestMethod
            headers { signed.headers.forEach { (name, value) -> append(name, value) } }
            setBody(body)
        }
        response.requireSuccess(operation)
        Log.d(TAG, "$operation success: $key")
    }

    private suspend fun executeObjectRequest(key: String, requestMethod: HttpMethod): HttpResponse {
        val path = "/${key.trimStart('/')}"
        val signed = AwsSignatureV4.sign(
            config = config,
            method = requestMethod.value,
            path = path,
            crypto = crypto,
        )
        val response = httpClient.request(signed.url) {
            method = requestMethod
            headers { signed.headers.forEach { (name, value) -> append(name, value) } }
        }
        response.requireSuccess(requestMethod.value.lowercase())
        return response
    }

    private suspend fun HttpResponse.requireSuccess(operation: String) {
        if (status.isSuccess()) return
        val errorBody = bodyAsText()
        Log.e(TAG, "$operation failed: $status - $errorBody")
        throw S3Exception("Failed to $operation: $status", errorBody)
    }

    private fun parseListObjectsResponse(xml: String): S3ListResult {
        val elements = Ksoup.parseXml(xml).getAllElements()
        val objects = elements
            .filter { it.hasLocalName("Contents") }
            .mapNotNull { content ->
                val key = content.descendantText("Key") ?: return@mapNotNull null
                S3Object(
                    key = key,
                    size = content.descendantText("Size")?.toLongOrNull() ?: 0,
                    etag = content.descendantText("ETag")?.trim('"'),
                    lastModified = content.descendantText("LastModified")
                        ?.let { runCatching { Instant.parse(it) }.getOrNull() },
                    storageClass = content.descendantText("StorageClass"),
                )
            }
        val commonPrefixes = elements
            .filter { it.hasLocalName("CommonPrefixes") }
            .mapNotNull { it.descendantText("Prefix") }
        val keyCount = elements.firstOrNull { it.hasLocalName("KeyCount") }
            ?.text()
            ?.trim()
            ?.toIntOrNull()
            ?: 0
        return S3ListResult(
            objects = objects,
            commonPrefixes = commonPrefixes,
            isTruncated = elements.firstOrNull { it.hasLocalName("IsTruncated") }
                ?.text()
                ?.trim()
                ?.toBoolean() == true,
            nextContinuationToken = elements.firstOrNull { it.hasLocalName("NextContinuationToken") }
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            keyCount = if (keyCount > 0) keyCount else objects.size,
        )
    }
}

private fun Element.hasLocalName(name: String): Boolean =
    tagName().substringAfter(':').equals(name, ignoreCase = true)

private fun Element.descendantText(name: String): String? = getAllElements()
    .firstOrNull { it !== this && it.hasLocalName(name) }
    ?.text()
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

data class S3Object(
    val key: String,
    val size: Long,
    val etag: String?,
    val lastModified: Instant?,
    val storageClass: String?,
)

data class S3ObjectMetadata(
    val key: String,
    val size: Long,
    val contentType: String,
    val etag: String?,
    val lastModified: String?,
)

data class S3ListResult(
    val objects: List<S3Object>,
    val commonPrefixes: List<String>,
    val isTruncated: Boolean,
    val nextContinuationToken: String?,
    val keyCount: Int,
)

class S3Exception(
    message: String,
    val responseBody: String,
) : Exception(message)
