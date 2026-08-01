package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.toLocalString
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant

internal fun buildTimeInfoTool(
    clock: Clock = Clock.System,
    timeZoneProvider: () -> TimeZone = { TimeZone.currentSystemDefault() },
    localeProvider: () -> Locale = { Locale.getDefault() },
): Tool = Tool(
    name = "get_time_info",
    description = """
        Get the current local date and time info from the device.
        Returns year/month/day, weekday, ISO date/time strings, timezone, and timestamp.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject { }
        )
    },
    execute = {
        val payload = buildTimeInfoPayload(
            instant = clock.now(),
            timeZone = timeZoneProvider(),
            locale = localeProvider(),
        )
        listOf(UIMessagePart.Text(payload.toString()))
    }
)

internal fun buildTimeInfoPayload(
    instant: Instant,
    timeZone: TimeZone,
    locale: Locale,
) = instant.toLocalDateTime(timeZone).let { now ->
    val date = now.date
    val time = LocalTime(now.hour, now.minute, now.second)
    val localDateTime = LocalDateTime(date, time)
    val weekday = date.dayOfWeek
    val offset = timeZone.offsetAt(instant)
    val zoneSuffix = if (timeZone.id == offset.toString()) "" else "[${timeZone.id}]"
    buildJsonObject {
        put("year", date.year)
        put("month", date.month.number)
        put("day", date.day)
        put("weekday", weekday.toLocalString(locale))
        put("weekday_en", weekday.toLocalString(Locale.ENGLISH))
        put("weekday_index", weekday.ordinal + 1)
        put("date", date.toString())
        put("time", time.toString())
        put("datetime", "$localDateTime$offset$zoneSuffix")
        put("timezone", timeZone.id)
        put("utc_offset", offset.toString())
        put("timestamp_ms", instant.toEpochMilliseconds())
    }
}
