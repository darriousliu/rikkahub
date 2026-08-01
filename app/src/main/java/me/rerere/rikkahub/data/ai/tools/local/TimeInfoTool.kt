package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.datetime.TimeZone
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.time.format.TextStyle
import java.time.ZoneId
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant

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
) = instant.toJavaInstant().atZone(ZoneId.of(timeZone.id)).let { now ->
    val date = now.toLocalDate()
    val time = now.toLocalTime().withNano(0)
    val weekday = now.dayOfWeek
    buildJsonObject {
        put("year", date.year)
        put("month", date.monthValue)
        put("day", date.dayOfMonth)
        put("weekday", weekday.getDisplayName(TextStyle.FULL, locale))
        put("weekday_en", weekday.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
        put("weekday_index", weekday.value)
        put("date", date.toString())
        put("time", time.toString())
        put("datetime", now.withNano(0).toString())
        put("timezone", now.zone.id)
        put("utc_offset", now.offset.id)
        put("timestamp_ms", now.toInstant().toEpochMilli())
    }
}
