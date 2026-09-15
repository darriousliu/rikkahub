package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
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
import me.rerere.rikkahub.shared.PlatformKind
import me.rerere.rikkahub.shared.currentPlatformKind
import kotlin.time.Clock
import kotlin.time.Instant

internal fun buildScreenTimeTool(access: ScreenTimeAccess = screenTimeAccess): Tool = Tool(
    name = "get_screen_time",
    description = """
        Get the user's app screen usage (screen time) over a time range.
        Specify a custom interval with 'begin'/'end', or use the 'range' preset (today/week).
        Returns the total foreground time and a per-app breakdown sorted by usage time (descending).
        The device timezone is '${TimeZone.currentSystemDefault().id}' (UTC offset ${currentLocalToolUtcOffset()});
        times without an explicit offset are interpreted in this timezone.
        ${if (currentPlatformKind == PlatformKind.IOS) {
            "Requires iOS 26.4 or later and authorized Screen Time app and website data access. " +
                "Enable it in the assistant's local tools settings. System or regional restrictions are returned as errors."
        } else {
            "Requires the 'Usage access' special permission; if it is not granted, the device's usage " +
                "access settings page is opened automatically and an error is returned."
        }}
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
                        }
                    )
                    put(
                        "description",
                        "Convenience preset, used only when 'begin' is omitted: today or week. Default today."
                    )
                })
                put("top", buildJsonObject {
                    put("type", "integer")
                    put("description", "Maximum number of top apps to return, sorted by usage time. Default 10.")
                })
            }
        )
    },
    execute = {
        try {
            access.checkPermission()
        } catch (e: LocalToolAccessException) {
            return@Tool listOf(UIMessagePart.Text(e.payload().toString()))
        }

        val params = it.jsonObject
        val top = params["top"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.coerceIn(1, 50) ?: 10

        val now = Clock.System.now()
        val zone = TimeZone.currentSystemDefault()
        val nowLocal = now.toLocalDateTime(zone)
        val beginRaw = params["begin"]?.jsonPrimitive?.contentOrNull
        val endRaw = params["end"]?.jsonPrimitive?.contentOrNull
        val rangePreset = params["range"]?.jsonPrimitive?.contentOrNull ?: "today"

        val startTime: Instant
        val endTime: Instant
        try {
            endTime = endRaw?.let { raw -> parseUsageTime(raw, zone) } ?: now
            startTime = if (beginRaw != null) {
                parseUsageTime(beginRaw, zone)
            } else when (rangePreset) {
                "week" -> LocalDateTime(
                    nowLocal.date.minus(7, DateTimeUnit.DAY),
                    nowLocal.time,
                ).toInstant(zone)
                else -> nowLocal.date.atStartOfDayIn(zone)
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

        val isCustom = beginRaw != null || endRaw != null
        val endMs = endTime.toEpochMilliseconds()
        val startMs = startTime.toEpochMilliseconds()

        val usage = try {
            access.query(startMs, endMs)
        } catch (e: LocalToolAccessException) {
            return@Tool listOf(UIMessagePart.Text(e.payload().toString()))
        }

        val sorted = usage
            .filter { entry -> entry.totalMillis > 0 }
            .sortedByDescending { entry -> entry.totalMillis }

        val totalMs = sorted.sumOf { entry -> entry.totalMillis }
        val apps = sorted.take(top)

        val payload = buildJsonObject {
            put("range", if (isCustom) "custom" else rangePreset)
            put("start", startTime.toLocalToolDateTimeString(zone))
            put("end", endTime.toLocalToolDateTimeString(zone))
            put("total_ms", totalMs)
            put("total_minutes", totalMs / 60000)
            put("apps", buildJsonArray {
                apps.forEach { entry ->
                    add(buildJsonObject {
                        put("package", entry.appId)
                        put("app_name", entry.appName)
                        put("total_ms", entry.totalMillis)
                        put("total_minutes", entry.totalMillis / 60000)
                    })
                }
            })
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)

/**
 * 解析 begin/end 时间参数, 依次尝试: epoch 毫秒 -> 带偏移日期时间 -> Instant ->
 * 本地日期时间 -> 本地日期(当天 0 点). 全部失败时抛出异常.
 */
private fun parseUsageTime(raw: String, timeZone: TimeZone): Instant =
    Instant.fromEpochMilliseconds(parseLocalToolTimeEpochMillis(raw, timeZone.id))
