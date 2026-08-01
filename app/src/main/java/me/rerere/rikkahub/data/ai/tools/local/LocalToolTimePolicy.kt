package me.rerere.rikkahub.data.ai.tools.local

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

internal fun parseLocalToolTimeEpochMillis(raw: String, timeZoneId: String): Long {
    val text = raw.trim()
    val zone = ZoneId.of(timeZoneId)
    text.toLongOrNull()?.let { return Instant.ofEpochMilli(it).toEpochMilli() }
    runCatching { return OffsetDateTime.parse(text).atZoneSameInstant(zone).toInstant().toEpochMilli() }
    runCatching { return Instant.parse(text).toEpochMilli() }
    runCatching { return LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli() }
    runCatching { return LocalDate.parse(text).atStartOfDay(zone).toInstant().toEpochMilli() }
    error("Invalid time format: '$raw'. Use ISO-8601 date/date-time or epoch milliseconds.")
}
