package me.rerere.rikkahub.data.sync.s3

import co.touchlab.kermit.Logger
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.parser.Parser
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.sink
import io.github.vinceglb.filekit.size
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlin.time.Instant

private const val TAG = "S3Client"

class S3Client(
    private val config: S3Config,
    private val httpClient: HttpClient,
) {
    suspend fun putObject(
        key: String,
        data: ByteArray,
        contentType: String = "application/octet-stream",
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val path = "/${key.trimStart('/')}"
            val signed = AwsSignatureV4.sign(
                config = config,
                method = "PUT",
                path = path,
                payload = data,
                contentType = contentType,
            )

            val response: HttpResponse = httpClient.request(signed.url) {
                method = HttpMethod.Put
                headers {
                    signed.headers.forEach { (k, v) -> append(k, v) }
                }
                setBody(data)
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                Logger.e(TAG) { "putObject failed: ${response.status} - $errorBody" }
                throw S3Exception("Failed to put object: ${response.status}", errorBody)
            }

            Logger.d(TAG) { "putObject success: $key" }
        }
    }

    suspend fun putObject(
        key: String,
        file: PlatformFile,
        contentType: String = "application/octet-stream",
    ): Result<Unit> = withContext(Dispatchers.IO) {
        putObject(key, file.readBytes(), contentType)
    }

    suspend fun getObject(key: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val path = "/${key.trimStart('/')}"
            val signed = AwsSignatureV4.sign(
                config = config,
                method = "GET",
                path = path,
            )

            val response: HttpResponse = httpClient.request(signed.url) {
                method = HttpMethod.Get
                headers {
                    signed.headers.forEach { (k, v) -> append(k, v) }
                }
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                Logger.e(TAG) { "getObject failed: ${response.status} - $errorBody" }
                throw S3Exception("Failed to get object: ${response.status}", errorBody)
            }

            response.readRawBytes()
        }
    }

    suspend fun getObjectStream(key: String): Result<ByteReadChannel> = withContext(Dispatchers.IO) {
        runCatching {
            val path = "/${key.trimStart('/')}"
            val signed = AwsSignatureV4.sign(
                config = config,
                method = "GET",
                path = path,
            )

            val response: HttpResponse = httpClient.request(signed.url) {
                method = HttpMethod.Get
                headers {
                    signed.headers.forEach { (k, v) -> append(k, v) }
                }
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                Logger.e(TAG) { "getObjectStream failed: ${response.status} - $errorBody" }
                throw S3Exception("Failed to get object stream: ${response.status}", errorBody)
            }

            response.bodyAsChannel()
        }
    }

    suspend fun downloadObjectToFile(key: String, targetFile: PlatformFile): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val path = "/${key.trimStart('/')}"
                val signed = AwsSignatureV4.sign(
                    config = config,
                    method = "GET",
                    path = path,
                )

                Logger.d(TAG) { "GET (download to file): $key" }

                httpClient.prepareRequest(signed.url) {
                    method = HttpMethod.Get
                    headers {
                        signed.headers.forEach { (k, v) -> append(k, v) }
                    }
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        val errorBody = response.bodyAsText()
                        Logger.e(TAG) { "downloadObjectToFile failed: ${response.status} - $errorBody" }
                        throw S3Exception("Failed to download object: ${response.status}", errorBody)
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
                    Logger.d(TAG) { "downloadObjectToFile success: downloaded ${targetFile.size()} bytes" }
                }
            }
        }

    suspend fun deleteObject(key: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val path = "/${key.trimStart('/')}"
            val signed = AwsSignatureV4.sign(
                config = config,
                method = "DELETE",
                path = path,
            )

            val response: HttpResponse = httpClient.request(signed.url) {
                method = HttpMethod.Delete
                headers {
                    signed.headers.forEach { (k, v) -> append(k, v) }
                }
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                Logger.e(TAG) { "deleteObject failed: ${response.status} - $errorBody" }
                throw S3Exception("Failed to delete object: ${response.status}", errorBody)
            }

            Logger.d(TAG) { "deleteObject success: $key" }
        }
    }

    suspend fun headObject(key: String): Result<S3ObjectMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val path = "/${key.trimStart('/')}"
            val signed = AwsSignatureV4.sign(
                config = config,
                method = "HEAD",
                path = path,
            )

            val response: HttpResponse = httpClient.request(signed.url) {
                method = HttpMethod.Head
                headers {
                    signed.headers.forEach { (k, v) -> append(k, v) }
                }
            }

            if (!response.status.isSuccess()) {
                throw S3Exception("Object not found: ${response.status}", "")
            }

            S3ObjectMetadata(
                key = key,
                size = response.headers["content-length"]?.toLongOrNull() ?: 0,
                contentType = response.headers["content-type"] ?: "application/octet-stream",
                etag = response.headers["etag"]?.trim('"'),
                lastModified = response.headers["last-modified"],
            )
        }
    }

    suspend fun listObjects(
        prefix: String = "",
        delimiter: String = "",
        maxKeys: Int = 1000,
        continuationToken: String? = null,
    ): Result<S3ListResult> = withContext(Dispatchers.IO) {
        runCatching {
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
            )

            val response: HttpResponse = httpClient.request(signed.url) {
                method = HttpMethod.Get
                headers {
                    signed.headers.forEach { (k, v) -> append(k, v) }
                }
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                Logger.e(TAG) { "listObjects failed: ${response.status} - $errorBody" }
                throw S3Exception("Failed to list objects: ${response.status}", errorBody)
            }

            val xmlBody = response.bodyAsText()
            parseListObjectsResponse(xmlBody)
        }
    }

    suspend fun objectExists(key: String): Boolean {
        return headObject(key).isSuccess
    }

    fun getPublicUrl(key: String): String {
        val path = "/${key.trimStart('/')}"
        return if (config.pathStyle) {
            "${config.endpoint.trimEnd('/')}/${config.bucket}$path"
        } else {
            val scheme = if (config.isHttps) "https://" else "http://"
            "$scheme${config.bucket}.${config.host}$path"
        }
    }

    private fun parseListObjectsResponse(xml: String): S3ListResult {
        val doc = Ksoup.parse(xml, Parser.xmlParser())
        // 顶层字段
        val isTruncated = doc.selectFirst("IsTruncated")?.text()?.toBoolean() ?: false
        val nextContinuationToken = doc.selectFirst("NextContinuationToken")?.text()?.takeIf { it.isNotBlank() }
        val keyCount = doc.selectFirst("KeyCount")?.text()?.toIntOrNull() ?: 0

        // 解析 Contents -> S3Object
        val objects = doc.select("Contents").mapNotNull { el ->
            val key = el.selectFirst("Key")?.text() ?: return@mapNotNull null
            S3Object(
                key = key,
                size = el.selectFirst("Size")?.text()?.toLongOrNull() ?: 0L,
                etag = el.selectFirst("ETag")?.text()?.trim('"'),
                lastModified = el.selectFirst("LastModified")?.text()?.let {
                    runCatching { Instant.parse(it) }.getOrNull()
                },
                storageClass = el.selectFirst("StorageClass")?.text()?.takeIf { it.isNotBlank() },
            )
        }

        // 解析 CommonPrefixes -> Prefix
        val commonPrefixes = doc.select("CommonPrefixes > Prefix")
            .mapNotNull { it.text().takeIf(String::isNotBlank) }

        return S3ListResult(
            objects = objects,
            commonPrefixes = commonPrefixes,
            isTruncated = isTruncated,
            nextContinuationToken = nextContinuationToken,
            keyCount = if (keyCount > 0) keyCount else objects.size,
        )
    }
}

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
