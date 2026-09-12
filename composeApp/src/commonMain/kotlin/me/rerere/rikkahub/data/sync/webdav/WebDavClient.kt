package me.rerere.rikkahub.data.sync.webdav

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.basicAuth
import io.ktor.client.request.headers
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.fromHttpToGmtDate
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import io.ktor.utils.io.readAvailable
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.common.logging.RikkaLog as Log
import me.rerere.rikkahub.data.datastore.WebDavConfig
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
            Log.d(TAG, "PUT: $url")

            val parsedContentType = ContentType.parse(contentType)
            val requestBody = object : OutgoingContent.ByteArrayContent() {
                override val contentLength = data.size.toLong()
                override val contentType = parsedContentType
                override fun bytes(): ByteArray = data
            }

            val response: HttpResponse = httpClient.request(url) {
                method = HttpMethod.Put
                basicAuth(config.username, config.password)
                headers {
                    append("Content-Type", contentType)
                    append("Content-Length", data.size.toString())
                }
                setBody(requestBody)
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "put failed: ${response.status} - $errorBody")
                throw WebDavException("Failed to put: ${response.status}", response.status.value, errorBody)
            }

            Log.d(TAG, "put success: $path")
            Unit
        }
    }

    suspend fun put(
        path: String,
        contentLength: Long,
        content: () -> ByteReadChannel,
        contentType: String = "application/octet-stream",
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = config.buildUrl(path)
            Log.d(TAG, "PUT (stream file): $url")

            val resolvedContentLength = contentLength
            val parsedContentType = ContentType.parse(contentType)
            val requestBody = object : OutgoingContent.ReadChannelContent() {
                override val contentLength = resolvedContentLength
                override val contentType = parsedContentType
                override fun readFrom(): ByteReadChannel = content()
            }

            val response: HttpResponse = httpClient.request(url) {
                method = HttpMethod.Put
                basicAuth(config.username, config.password)
                headers {
                    append("Content-Type", contentType)
                    append("Content-Length", contentLength.toString())
                }
                // Stream file content to avoid loading the whole backup zip into heap.
                setBody(requestBody)
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "put(file) failed: ${response.status} - $errorBody")
                throw WebDavException("Failed to put file: ${response.status}", response.status.value, errorBody)
            }

            Log.d(TAG, "put(file) success: $path (${contentLength} bytes)")
            Unit
        }
    }

    suspend fun get(path: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val url = config.buildUrl(path)
            Log.d(TAG, "GET: $url")

            val response: HttpResponse = httpClient.request(url) {
                method = HttpMethod.Get
                basicAuth(config.username, config.password)
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "get failed: ${response.status} - $errorBody")
                throw WebDavException("Failed to get: ${response.status}", response.status.value, errorBody)
            }

            response.body<ByteArray>()
        }
    }

    suspend fun download(
        path: String,
        writeChunk: suspend (buffer: ByteArray, byteCount: Int) -> Unit,
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val url = config.buildUrl(path)
            var downloaded = 0L
            httpClient.prepareRequest(url) {
                method = HttpMethod.Get
                basicAuth(config.username, config.password)
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val errorBody = response.bodyAsText()
                    Log.e(TAG, "download failed: ${response.status} - $errorBody")
                    throw WebDavException("Failed to download: ${response.status}", response.status.value, errorBody)
                }
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
            Log.d(TAG, "download success: $path ($downloaded bytes)")
            downloaded
        }
    }

    suspend fun delete(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = config.buildUrl(path)
            Log.d(TAG, "DELETE: $url")

            val response: HttpResponse = httpClient.request(url) {
                method = HttpMethod.Delete
                basicAuth(config.username, config.password)
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "delete failed: ${response.status} - $errorBody")
                throw WebDavException("Failed to delete: ${response.status}", response.status.value, errorBody)
            }

            Log.d(TAG, "delete success: $path")
            Unit
        }
    }

    suspend fun head(path: String): Result<WebDavResourceInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val url = config.buildUrl(path)
            Log.d(TAG, "HEAD: $url")

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
                lastModified = parseWebDavLastModified(response.headers["Last-Modified"]),
                isCollection = false,
            )
        }
    }

    suspend fun mkcol(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = config.buildUrl(path)
            Log.d(TAG, "MKCOL: $url")

            val response: HttpResponse = httpClient.request(url) {
                method = HttpMethod("MKCOL")
                basicAuth(config.username, config.password)
            }

            // 201 Created or 405 Method Not Allowed (already exists) are acceptable
            if (!response.status.isSuccess() && response.status != HttpStatusCode.MethodNotAllowed) {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "mkcol failed: ${response.status} - $errorBody")
                throw WebDavException("Failed to create collection: ${response.status}", response.status.value, errorBody)
            }

            Log.d(TAG, "mkcol success: $path")
            Unit
        }
    }

    suspend fun propfind(
        path: String = "",
        depth: Int = 1,
    ): Result<List<WebDavResourceInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = config.buildUrl(path)
            Log.d(TAG, "PROPFIND: $url, depth: $depth")

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
                Log.e(TAG, "propfind failed: ${response.status} - $errorBody")
                throw WebDavException("Failed to propfind: ${response.status}", response.status.value, errorBody)
            }

            val xmlBody = response.bodyAsText()
            parsePropfindResponse(xmlBody)
        }
    }

    suspend fun exists(path: String): Boolean {
        return head(path).isSuccess
    }

    suspend fun ensureCollectionExists(path: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val targetUrl = config.buildUrl(path)
            Log.d(TAG, "Ensuring collection exists: $targetUrl")

            // Try propfind first to check if it exists
            val propfindResult = propfind(path, depth = 0)
            if (propfindResult.isSuccess) {
                Log.d(TAG, "Collection already exists: $targetUrl")
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

    private fun parsePropfindResponse(xml: String): List<WebDavResourceInfo> = Ksoup.parseXml(xml)
        .getAllElements()
        .filter { it.hasLocalName("response") }
        .mapNotNull { response ->
            val href = response.descendantText("href") ?: return@mapNotNull null
            WebDavResourceInfo(
                href = href,
                displayName = response.descendantText("displayname")
                    ?: href.trimEnd('/').substringAfterLast("/"),
                contentLength = response.descendantText("getcontentlength")?.toLongOrNull() ?: 0,
                contentType = response.descendantText("getcontenttype") ?: "application/octet-stream",
                lastModified = parseWebDavLastModified(response.descendantText("getlastmodified")),
                isCollection = response.getAllElements().any { it.hasLocalName("collection") },
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

internal fun parseWebDavLastModified(dateString: String?): Instant? {
    if (dateString.isNullOrBlank()) return null
    val legacyRfc850Match = RFC_850_DATE_REGEX.matchEntire(dateString)
    if (legacyRfc850Match != null) return parseLegacyRfc850Date(legacyRfc850Match)
    return runCatching {
        Instant.fromEpochMilliseconds(dateString.fromHttpToGmtDate().timestamp)
    }.getOrNull() ?: runCatching {
        Instant.parse(dateString)
    }.getOrElse {
        Log.w(TAG, "Failed to parse date: $dateString")
        null
    }
}

private val RFC_850_DATE_REGEX = Regex(
    """([A-Za-z]+), (\d{2})-([A-Za-z]{3})-(\d{2}) (\d{2}):(\d{2}):(\d{2}) GMT"""
)

private val RFC_850_MONTHS = mapOf(
    "Jan" to Month.JANUARY,
    "Feb" to Month.FEBRUARY,
    "Mar" to Month.MARCH,
    "Apr" to Month.APRIL,
    "May" to Month.MAY,
    "Jun" to Month.JUNE,
    "Jul" to Month.JULY,
    "Aug" to Month.AUGUST,
    "Sep" to Month.SEPTEMBER,
    "Oct" to Month.OCTOBER,
    "Nov" to Month.NOVEMBER,
    "Dec" to Month.DECEMBER,
)

private val RFC_850_WEEKDAYS = mapOf(
    "Monday" to DayOfWeek.MONDAY,
    "Tuesday" to DayOfWeek.TUESDAY,
    "Wednesday" to DayOfWeek.WEDNESDAY,
    "Thursday" to DayOfWeek.THURSDAY,
    "Friday" to DayOfWeek.FRIDAY,
    "Saturday" to DayOfWeek.SATURDAY,
    "Sunday" to DayOfWeek.SUNDAY,
)

private fun parseLegacyRfc850Date(match: MatchResult): Instant? = runCatching {
    val (weekday, day, month, reducedYear, hour, minute, second) = match.destructured
    val dateTime = LocalDateTime(
        year = 2000 + reducedYear.toInt(),
        month = RFC_850_MONTHS.getValue(month),
        day = day.toInt(),
        hour = hour.toInt(),
        minute = minute.toInt(),
        second = second.toInt(),
    )
    require(dateTime.dayOfWeek == RFC_850_WEEKDAYS[weekday])
    dateTime.toInstant(TimeZone.UTC)
}.getOrNull()

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
