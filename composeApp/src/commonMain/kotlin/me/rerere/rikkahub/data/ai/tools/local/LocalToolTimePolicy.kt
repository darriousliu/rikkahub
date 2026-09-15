package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

internal fun parseLocalToolTimeEpochMillis(raw: String, timeZoneId: String): Long {
    val text = raw.trim()
    val timeZone = TimeZone.of(timeZoneId)
    text.toLongOrNull()?.let { return Instant.fromEpochMilliseconds(it).toEpochMilliseconds() }
    runCatching { return Instant.parse(text).toEpochMilliseconds() }
    runCatching { return LocalDateTime.parse(text).toInstant(timeZone).toEpochMilliseconds() }
    runCatching { return LocalDate.parse(text).atStartOfDayIn(timeZone).toEpochMilliseconds() }
    error("Invalid time format: '$raw'. Use ISO-8601 date/date-time or epoch milliseconds.")
}

internal fun currentLocalToolUtcOffset(
    instant: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String = timeZone.offsetAt(instant).toString()

internal fun Instant.toLocalToolDateTimeString(timeZone: TimeZone): String {
    val local = toLocalDateTime(timeZone)
    val localWithoutFraction = LocalDateTime(
        date = local.date,
        time = LocalTime(local.hour, local.minute, local.second),
    )
    val offset = timeZone.offsetAt(this)
    val zoneSuffix = if (timeZone.id == offset.toString()) "" else "[${timeZone.id}]"
    return "$localWithoutFraction$offset$zoneSuffix"
}
