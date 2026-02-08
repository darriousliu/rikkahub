package me.rerere.rikkahub.data.sync.s3

import io.ktor.utils.io.core.toByteArray
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

internal object AwsSignatureV4 {
    private const val ALGORITHM = "AWS4-HMAC-SHA256"
    private const val SERVICE = "s3"
    private const val UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD"

    private val dateFormatter = LocalDateTime.Format {
        year()
        monthNumber()
        day()
    }
    private val timestampFormatter = LocalDateTime.Format {
        year()
        monthNumber()
        day()
        char('T')
        hour()
        minute()
        second()
        char('Z')
    }

    data class SignedRequest(
        val headers: Map<String, String>,
        val url: String,
    )

    fun sign(
        config: S3Config,
        method: String,
        path: String,
        queryParams: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        payload: ByteArray? = null,
        contentType: String? = null,
    ): SignedRequest {
        val now = Clock.System.now()
        val utc = now.toLocalDateTime(TimeZone.UTC)
        val dateStamp = utc.format(dateFormatter)
        val amzDate = utc.format(timestampFormatter)

        val payloadHash = payload?.sha256Hex() ?: UNSIGNED_PAYLOAD

        val host = config.host
        val canonicalUri = if (config.pathStyle) {
            "/${config.bucket}$path"
        } else {
            path
        }.let { it.ifEmpty { "/" } }

        val allHeaders = mutableMapOf(
            "host" to (if (config.pathStyle) host else "${config.bucket}.$host"),
            "x-amz-content-sha256" to payloadHash,
            "x-amz-date" to amzDate,
        )
        contentType?.let { allHeaders["content-type"] = it }
        payload?.let { allHeaders["content-length"] = it.size.toString() }
        allHeaders.putAll(headers.mapKeys { it.key.lowercase() })

        val signedHeaders = allHeaders.keys.sorted().joinToString(";")
        val canonicalHeaders = allHeaders.entries
            .sortedBy { it.key }
            .joinToString("") { "${it.key}:${it.value.trim()}\n" }

        val canonicalQueryString = queryParams.entries
            .sortedBy { it.key }
            .joinToString("&") { "${it.key.urlEncode()}=${it.value.urlEncode()}" }

        val canonicalRequest = buildString {
            appendLine(method)
            appendLine(canonicalUri.urlEncodePath())
            appendLine(canonicalQueryString)
            append(canonicalHeaders)
            appendLine()
            appendLine(signedHeaders)
            append(payloadHash)
        }

        val credentialScope = "$dateStamp/${config.region}/$SERVICE/aws4_request"
        val stringToSign = buildString {
            appendLine(ALGORITHM)
            appendLine(amzDate)
            appendLine(credentialScope)
            append(canonicalRequest.sha256Hex())
        }

        val signingKey = getSignatureKey(
            config.secretAccessKey,
            dateStamp,
            config.region,
            SERVICE
        )
        val signature = hmacSha256(signingKey, stringToSign.encodeToByteArray()).toHexString()

        val authorizationHeader = buildString {
            append("$ALGORITHM ")
            append("Credential=${config.accessKeyId}/$credentialScope, ")
            append("SignedHeaders=$signedHeaders, ")
            append("Signature=$signature")
        }

        val resultHeaders = allHeaders.toMutableMap()
        resultHeaders["authorization"] = authorizationHeader

        val url = buildString {
            append(if (config.isHttps) "https://" else "http://")
            append(if (config.pathStyle) host else "${config.bucket}.$host")
            append(canonicalUri)
            if (canonicalQueryString.isNotEmpty()) {
                append("?$canonicalQueryString")
            }
        }

        return SignedRequest(
            headers = resultHeaders,
            url = url
        )
    }

    private fun getSignatureKey(
        key: String,
        dateStamp: String,
        region: String,
        service: String
    ): ByteArray {
        val kDate = hmacSha256("AWS4$key".encodeToByteArray(), dateStamp.encodeToByteArray())
        val kRegion = hmacSha256(kDate, region.encodeToByteArray())
        val kService = hmacSha256(kRegion, service.encodeToByteArray())
        return hmacSha256(kService, "aws4_request".encodeToByteArray())
    }

    private fun ByteArray.sha256Hex(): String = sha256(this).toHexString()

    private fun String.sha256Hex(): String = this.encodeToByteArray().sha256Hex()

    private fun ByteArray.toHexString(): String =
        joinToString("") { byte ->
            val hex = (byte.toInt() and 0xFF).toString(16)
            if (hex.length == 1) "0$hex" else hex
        }

    /**
     * AWS SigV4 要求的 URL 编码：RFC 3986 unreserved 字符不编码。
     * 参考 [docs.aws.amazon.com](https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_sigv.html)
     */
    private fun String.urlEncode(): String = buildString {
        val bytes = encodeToByteArray()
        for (b in bytes) {
            val c = b.toInt().toChar()
            when {
                c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' ||
                    c == '-' || c == '_' || c == '.' || c == '~' -> append(c)
                else -> {
                    val hex = (b.toInt() and 0xFF).toString(16).uppercase()
                    append('%')
                    if (hex.length == 1) append('0')
                    append(hex)
                }
            }
        }
    }

    private fun String.urlEncodePath(): String {
        return split("/").joinToString("/") { segment ->
            if (segment.isEmpty()) segment else segment.urlEncode()
        }
    }
}

internal expect fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray
internal expect fun sha256(data: ByteArray): ByteArray
