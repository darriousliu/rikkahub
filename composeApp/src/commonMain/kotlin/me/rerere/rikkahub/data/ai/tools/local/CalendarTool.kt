package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

internal fun buildCalendarQueryTool(calendar: CalendarAccess = calendarAccess): Tool = Tool(
    name = "calendar_query",
    description = """
        Query calendar events on the user's device within a time range.
        Specify a custom interval with 'begin'/'end', or use the 'range' preset (today/week/month).
        Returns a list of events with title, description, location, start/end times, and calendar info.
        The device timezone is '${TimeZone.currentSystemDefault().id}' (UTC offset ${currentLocalToolUtcOffset()});
        times without an explicit offset are interpreted in this timezone.
        Requires the 'Calendar' permission; if it is not granted, an error is returned and the
        permission request is triggered automatically.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("begin", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        "Start time (inclusive). Accepts an ISO-8601 date 'yyyy-MM-dd', a local " +
                            "date-time 'yyyy-MM-ddTHH:mm:ss', an offset date-time, or epoch milliseconds. " +
                            "When provided, 'range' is ignored."
                    )
                })
                put("end", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        "End time (exclusive), same formats as 'begin'. Defaults to now."
                    )
                })
                put("range", buildJsonObject {
                    put("type", "string")
                    put(
                        "enum",
                        buildJsonArray {
                            add("today")
                            add("week")
                            add("month")
                        }
                    )
                    put(
                        "description",
                        "Convenience preset, used only when 'begin' is omitted: today, week, or month. Default today."
                    )
                })
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional keyword to filter events by title (case-insensitive substring match).")
                })
                put("limit", buildJsonObject {
                    put("type", "integer")
                    put("description", "Maximum number of events to return. Default 20.")
                })
            }
        )
    },
    execute = { args ->
        if (!calendar.hasReadPermission()) {
            val payload = buildJsonObject {
                put("error", "NO_PERMISSION")
                put(
                    "message",
                    "Calendar read permission is not granted. Please ask the user to enable " +
                        "the calendar permission in the assistant's local tools settings."
                )
            }
            return@Tool listOf(UIMessagePart.Text(payload.toString()))
        }

        val params = args.jsonObject
        val limit = params["limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.coerceIn(1, 100) ?: 20
        val query = params["query"]?.jsonPrimitive?.contentOrNull

        val now = Clock.System.now()
        val zone = TimeZone.currentSystemDefault()
        val today = now.toLocalDateTime(zone).date
        val beginRaw = params["begin"]?.jsonPrimitive?.contentOrNull
        val endRaw = params["end"]?.jsonPrimitive?.contentOrNull
        val rangePreset = params["range"]?.jsonPrimitive?.contentOrNull ?: "today"

        val startTime: Instant
        val endTime: Instant
        try {
            startTime = if (beginRaw != null) {
                parseCalendarTime(beginRaw, zone)
            } else when (rangePreset) {
                "week" -> today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY).atStartOfDayIn(zone)
                "month" -> LocalDate(today.year, today.month, 1).atStartOfDayIn(zone)
                else -> today.atStartOfDayIn(zone)
            }
            endTime = if (endRaw != null) {
                parseCalendarTime(endRaw, zone)
            } else when (rangePreset) {
                "week" -> startTime.plusLocalDateUnits(7, DateTimeUnit.DAY, zone)
                "month" -> startTime.plusLocalDateUnits(1, DateTimeUnit.MONTH, zone)
                else -> today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
            }
        } catch (e: Exception) {
            val payload = buildJsonObject {
                put("error", "INVALID_TIME")
                put("message", e.message ?: "Invalid time format for begin/end.")
            }
            return@Tool listOf(UIMessagePart.Text(payload.toString()))
        }

        if (startTime >= endTime) {
            val payload = buildJsonObject {
                put("error", "INVALID_RANGE")
                put("message", "begin must be earlier than end.")
            }
            return@Tool listOf(UIMessagePart.Text(payload.toString()))
        }

        val startMs = startTime.toEpochMilliseconds()
        val endMs = endTime.toEpochMilliseconds()

        val events = buildJsonArray {
            calendar.query(startMs, endMs, query, limit).forEach { event ->
                add(buildJsonObject {
                    put("id", event.id)
                    put("title", event.title)
                    put("description", event.description)
                    put("location", event.location)
                    if (event.allDay) {
                        put("start", Instant.fromEpochMilliseconds(event.startMillis).toLocalDateTime(TimeZone.UTC).date.toString())
                        put("end", if (event.endMillis > 0) {
                            Instant.fromEpochMilliseconds(event.endMillis).toLocalDateTime(TimeZone.UTC).date.toString()
                        } else "")
                    } else {
                        put("start", Instant.fromEpochMilliseconds(event.startMillis).toLocalToolDateTimeString(zone))
                        put("end", if (event.endMillis > 0) {
                            Instant.fromEpochMilliseconds(event.endMillis).toLocalToolDateTimeString(zone)
                        } else "")
                    }
                    put("all_day", event.allDay)
                    put("calendar", event.calendar)
                })
            }
        }

        val payload = buildJsonObject {
            put("range_start", startTime.toLocalToolDateTimeString(zone))
            put("range_end", endTime.toLocalToolDateTimeString(zone))
            put("count", events.size)
            put("events", events)
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)

internal fun buildCalendarCreateTool(calendar: CalendarAccess = calendarAccess): Tool = Tool(
    name = "calendar_create",
    description = """
        Create a new calendar event on the user's device.
        Requires title and start time at minimum. End time defaults to 1 hour after start.
        The device timezone is '${TimeZone.currentSystemDefault().id}' (UTC offset ${currentLocalToolUtcOffset()});
        times without an explicit offset are interpreted in this timezone.
        Requires the 'Calendar' permission; if it is not granted, an error is returned and the
        permission request is triggered automatically.
    """.trimIndent().replace("\n", " "),
    needsApproval = { true },
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("title", buildJsonObject {
                    put("type", "string")
                    put("description", "Event title.")
                })
                put("description", buildJsonObject {
                    put("type", "string")
                    put("description", "Event description or notes.")
                })
                put("location", buildJsonObject {
                    put("type", "string")
                    put("description", "Event location.")
                })
                put("start", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        "Start time. Accepts an ISO-8601 date 'yyyy-MM-dd', a local " +
                            "date-time 'yyyy-MM-ddTHH:mm:ss', an offset date-time, or epoch milliseconds."
                    )
                })
                put("end", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        "End time, same formats as 'start'. Defaults to 1 hour after start."
                    )
                })
                put("all_day", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Whether this is an all-day event. Default false.")
                })
            },
            required = listOf("title", "start")
        )
    },
    execute = { args ->
        if (!calendar.hasWritePermission()) {
            val payload = buildJsonObject {
                put("error", "NO_PERMISSION")
                put(
                    "message",
                    "Calendar write permission is not granted. Please ask the user to enable " +
                        "the calendar permission in the assistant's local tools settings."
                )
            }
            return@Tool listOf(UIMessagePart.Text(payload.toString()))
        }

        val params = args.jsonObject
        val title = params["title"]?.jsonPrimitive?.contentOrNull
        val startRaw = params["start"]?.jsonPrimitive?.contentOrNull
        val endRaw = params["end"]?.jsonPrimitive?.contentOrNull
        val allDay = params["all_day"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false

        if (title.isNullOrBlank() || startRaw.isNullOrBlank()) {
            val payload = buildJsonObject {
                put("error", "MISSING_REQUIRED")
                put("message", "Both 'title' and 'start' are required.")
            }
            return@Tool listOf(UIMessagePart.Text(payload.toString()))
        }

        val zone = TimeZone.currentSystemDefault()
        val startTime: Instant
        val endTime: Instant
        try {
            startTime = parseCalendarTime(startRaw, zone)
            endTime = if (endRaw != null) {
                parseCalendarTime(endRaw, zone)
            } else if (allDay) {
                startTime.toLocalDateTime(zone).date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
            } else {
                startTime + 1.hours
            }
        } catch (e: Exception) {
            val payload = buildJsonObject {
                put("error", "INVALID_TIME")
                put("message", e.message ?: "Invalid time format.")
            }
            return@Tool listOf(UIMessagePart.Text(payload.toString()))
        }

        if (startTime >= endTime) {
            val payload = buildJsonObject {
                put("error", "INVALID_RANGE")
                put("message", "end must be later than start.")
            }
            return@Tool listOf(UIMessagePart.Text(payload.toString()))
        }

        val description = params["description"]?.jsonPrimitive?.contentOrNull ?: ""
        val location = params["location"]?.jsonPrimitive?.contentOrNull ?: ""

        val eventStartMillis: Long
        val eventEndMillis: Long
        val eventTimeZone: String
        if (allDay) {
            val startDate = startTime.toLocalDateTime(zone).date
            val endDate = endTime.toLocalDateTime(zone).date
            if (startDate >= endDate) {
                val payload = buildJsonObject {
                    put("error", "INVALID_RANGE")
                    put("message", "all-day event end date must be later than start date.")
                }
                return@Tool listOf(UIMessagePart.Text(payload.toString()))
            }
            eventStartMillis = startDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
            eventEndMillis = endDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
            eventTimeZone = "UTC"
        } else {
            eventStartMillis = startTime.toEpochMilliseconds()
            eventEndMillis = endTime.toEpochMilliseconds()
            eventTimeZone = zone.id
        }

        val calendarId = calendar.defaultCalendarId()
        if (calendarId == null) {
            val payload = buildJsonObject {
                put("error", "NO_CALENDAR")
                put("message", "No calendar account found on this device. Please add a calendar account first.")
            }
            return@Tool listOf(UIMessagePart.Text(payload.toString()))
        }

        val eventId = calendar.create(calendarId, CalendarEventDraft(
            title = title, description = description, location = location,
            startMillis = eventStartMillis, endMillis = eventEndMillis,
            timeZoneId = eventTimeZone, allDay = allDay,
        ))
        if (eventId == null) {
            val payload = buildJsonObject {
                put("error", "INSERT_FAILED")
                put("message", "Failed to insert calendar event.")
            }
            return@Tool listOf(UIMessagePart.Text(payload.toString()))
        }

        val payload = buildJsonObject {
            put("success", true)
            put("event_id", eventId)
            put("title", title)
            put("start", startTime.toLocalToolDateTimeString(zone))
            put("end", endTime.toLocalToolDateTimeString(zone))
            put("all_day", allDay)
            put("location", location)
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)

private fun parseCalendarTime(raw: String, timeZone: TimeZone): Instant =
    Instant.fromEpochMilliseconds(parseLocalToolTimeEpochMillis(raw, timeZone.id))

private fun Instant.plusLocalDateUnits(
    value: Int,
    unit: DateTimeUnit.DateBased,
    timeZone: TimeZone,
): Instant = toLocalDateTime(timeZone).let { local ->
    LocalDateTime(local.date.plus(value, unit), local.time).toInstant(timeZone)
}
