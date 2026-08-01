package me.rerere.rikkahub.data.sync.s3

import io.ktor.http.encodeURLQueryComponent
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import me.rerere.common.crypto.Sha256Crypto
import kotlin.time.Clock
import kotlin.time.Instant

internal object AwsSignatureV4 {
    private const val ALGORITHM = "AWS4-HMAC-SHA256"
    private const val SERVICE = "s3"
    private const val UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD"

    data class SignedRequest(
        val headers: Map<String, String>,
        val url: String,
    )

    fun sign(
        config: S3Config,
        method: String,
        path: String,
        crypto: Sha256Crypto,
        queryParams: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        payload: ByteArray? = null,
        payloadHash: String? = null,
        contentLength: Long? = null,
        contentType: String? = null,
        now: Instant = Clock.System.now(),
    ): SignedRequest {
        val utc = now.toLocalDateTime(TimeZone.UTC)
        val dateStamp = buildString {
            append(utc.year.fixedWidth(4))
            append(utc.month.number.fixedWidth(2))
            append(utc.day.fixedWidth(2))
        }
        val amzDate = buildString {
            append(dateStamp)
            append('T')
            append(utc.hour.fixedWidth(2))
            append(utc.minute.fixedWidth(2))
            append(utc.second.fixedWidth(2))
            append('Z')
        }

        val resolvedPayloadHash = payloadHash ?: payload?.sha256Hex(crypto) ?: UNSIGNED_PAYLOAD
        val host = config.host
        val canonicalUri = if (config.pathStyle) {
            "/${config.bucket}$path"
        } else {
            path
        }.let { if (it.isEmpty()) "/" else it }
        val hostAlreadyContainsBucket = host.startsWith("${config.bucket}.")

        val allHeaders = mutableMapOf(
            "host" to when {
                config.pathStyle -> host
                hostAlreadyContainsBucket -> host
                else -> "${config.bucket}.$host"
            },
            "x-amz-content-sha256" to resolvedPayloadHash,
            "x-amz-date" to amzDate,
        )
        contentType?.let { allHeaders["content-type"] = it }
        payload?.let { allHeaders["content-length"] = it.size.toString() }
        contentLength?.let { allHeaders["content-length"] = it.toString() }
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
            append(resolvedPayloadHash)
        }
        val credentialScope = "$dateStamp/${config.region}/$SERVICE/aws4_request"
        val stringToSign = buildString {
            appendLine(ALGORITHM)
            appendLine(amzDate)
            appendLine(credentialScope)
            append(canonicalRequest.sha256Hex(crypto))
        }
        val signingKey = getSignatureKey(
            config.secretAccessKey,
            dateStamp,
            config.region,
            SERVICE,
            crypto,
        )
        val signature = crypto.hmac(signingKey, stringToSign.encodeToByteArray()).toHexString()
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
            append(
                when {
                    config.pathStyle -> host
                    hostAlreadyContainsBucket -> host
                    else -> "${config.bucket}.$host"
                }
            )
            append(canonicalUri)
            if (canonicalQueryString.isNotEmpty()) append("?$canonicalQueryString")
        }

        return SignedRequest(headers = resultHeaders, url = url)
    }

    private fun getSignatureKey(
        key: String,
        dateStamp: String,
        region: String,
        service: String,
        crypto: Sha256Crypto,
    ): ByteArray {
        val kDate = crypto.hmac("AWS4$key".encodeToByteArray(), dateStamp.encodeToByteArray())
        val kRegion = crypto.hmac(kDate, region.encodeToByteArray())
        val kService = crypto.hmac(kRegion, service.encodeToByteArray())
        return crypto.hmac(kService, "aws4_request".encodeToByteArray())
    }

    private fun ByteArray.sha256Hex(crypto: Sha256Crypto): String = crypto.digest(this).toHexString()

    private fun String.sha256Hex(crypto: Sha256Crypto): String = encodeToByteArray().sha256Hex(crypto)

    private fun ByteArray.toHexString(): String = buildString(size * 2) {
        for (byte in this@toHexString) {
            val value = byte.toInt() and 0xff
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0f])
        }
    }

    private fun String.urlEncode(): String = encodeURLQueryComponent(
        encodeFull = true,
        spaceToPlus = false,
    )
        .replace("%2D", "-")
        .replace("%2E", ".")
        .replace("%5F", "_")
        .replace("%7E", "~")

    private fun String.urlEncodePath(): String = split("/").joinToString("/") { segment ->
        if (segment.isEmpty()) segment else segment.urlEncode()
    }

    private fun Int.fixedWidth(width: Int): String = toString().padStart(width, '0')

    private const val HEX_DIGITS = "0123456789abcdef"
}
