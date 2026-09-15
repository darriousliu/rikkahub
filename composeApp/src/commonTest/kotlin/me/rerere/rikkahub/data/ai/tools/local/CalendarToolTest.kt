package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarToolTest {
    @Test
    fun toolNamesSchemaAndCreateApprovalRemainUnchanged() {
        val access = FakeCalendar()
        val query = buildCalendarQueryTool(access)
        val create = buildCalendarCreateTool(access)
        assertEquals("calendar_query", query.name)
        assertEquals("calendar_create", create.name)
        assertEquals(setOf("begin", "end", "range", "query", "limit"),
            (query.parameters() as InputSchema.Obj).properties.keys)
        assertEquals(listOf("title", "start"), (create.parameters() as InputSchema.Obj).required)
        assertTrue(create.needsApproval(JsonObject(emptyMap())))
    }

    @Test
    fun deniedPermissionDoesNotReadOrWriteCalendar() = runTest {
        val access = FakeCalendar().apply { allowed = false }
        assertEquals("NO_PERMISSION", buildCalendarQueryTool(access).result("{}")["error"]?.jsonPrimitive?.content)
        assertEquals("NO_PERMISSION", buildCalendarCreateTool(access).result("{}")["error"]?.jsonPrimitive?.content)
        assertNull(access.queryArgs)
        assertNull(access.created)
    }

    @Test
    fun queryPreservesRangeKeywordLimitIdsAndAllDayExclusiveEnd() = runTest {
        val access = FakeCalendar().apply {
            events = listOf(CalendarEvent(JsonPrimitive("apple-event-id"), "Meeting 中文", "Notes", "Room",
                1893456000000L, 1893542400000L, true, "Calendar"))
        }
        val result = buildCalendarQueryTool(access).result("""{
            "begin":"2030-01-01T00:00:00Z","end":"2030-01-03T00:00:00Z","query":"%meeting_","limit":999
        }""")
        assertEquals(listOf(1893456000000L, 1893628800000L, "%meeting_", 100), access.queryArgs)
        val event = result.getValue("events").jsonArray.single().jsonObject
        assertEquals(JsonPrimitive("apple-event-id"), event["id"])
        assertEquals(JsonPrimitive("2030-01-01"), event["start"])
        assertEquals(JsonPrimitive("2030-01-02"), event["end"])
        assertEquals(JsonPrimitive(1), result["count"])
    }

    @Test
    fun invalidTimeAndRangeDoNotQuery() = runTest {
        val access = FakeCalendar()
        assertEquals("INVALID_TIME", buildCalendarQueryTool(access).result("""{"begin":"invalid"}""")["error"]?.jsonPrimitive?.content)
        assertEquals("INVALID_RANGE", buildCalendarQueryTool(access).result("""{"begin":"2000","end":"1000"}""")["error"]?.jsonPrimitive?.content)
        assertNull(access.queryArgs)
    }

    @Test
    fun createDefaultsToOneHourAndPreservesNativeIdType() = runTest {
        val access = FakeCalendar()
        val result = buildCalendarCreateTool(access).result("""{"title":"A \"quoted\" 中文","start":"2030-01-01T00:00:00Z"}""")
        val event = assertNotNull(access.created)
        assertEquals(1893456000000L, event.startMillis)
        assertEquals(event.startMillis + 3600000L, event.endMillis)
        assertEquals("A \"quoted\" 中文", event.title)
        assertEquals(JsonPrimitive(123), result["event_id"])
        access.eventId = JsonPrimitive("native-string-id")
        assertEquals(access.eventId, buildCalendarCreateTool(access).result("""{"title":"B","start":"2030-01-01"}""")["event_id"])
    }

    @Test
    fun allDayUsesUtcDateStorageWithExclusiveEnd() = runTest {
        val access = FakeCalendar()
        buildCalendarCreateTool(access).result("""{"title":"All day","start":"2030-01-01","all_day":true}""")
        val event = assertNotNull(access.created)
        assertEquals("UTC", event.timeZoneId)
        assertEquals(1893456000000L, event.startMillis)
        assertEquals(1893542400000L, event.endMillis)
    }

    @Test
    fun missingTitleAndInvalidEndDoNotCreate() = runTest {
        val access = FakeCalendar()
        assertEquals("MISSING_REQUIRED", buildCalendarCreateTool(access).result("{}")["error"]?.jsonPrimitive?.content)
        assertEquals("INVALID_RANGE", buildCalendarCreateTool(access).result("""{"title":"A","start":"2000","end":"1000"}""")["error"]?.jsonPrimitive?.content)
        assertNull(access.created)
    }

    @Test
    fun missingCalendarAndFailedInsertPreserveOriginalErrors() = runTest {
        val access = FakeCalendar().apply { calendarId = null }
        val args = """{"title":"A","start":"2030-01-01"}"""
        assertEquals("NO_CALENDAR", buildCalendarCreateTool(access).result(args)["error"]?.jsonPrimitive?.content)
        assertNull(access.created)
        access.calendarId = "calendar"
        access.eventId = null
        assertEquals("INSERT_FAILED", buildCalendarCreateTool(access).result(args)["error"]?.jsonPrimitive?.content)
    }

    @Test
    fun nativeCancellationPropagates() = runTest {
        val access = FakeCalendar().apply { failure = CancellationException("cancelled") }
        assertFailsWith<CancellationException> {
            buildCalendarQueryTool(access).result("""{"begin":"1000","end":"2000"}""")
        }
    }

    @Test
    fun originalTimePolicyHandlesOffsetsAndDst() {
        assertEquals(1893456000000L, parseLocalToolTimeEpochMillis("2030-01-01T08:00:00+08:00", "UTC"))
        assertEquals(1893456000000L, parseLocalToolTimeEpochMillis("1893456000000", "Asia/Shanghai"))
        val start = parseLocalToolTimeEpochMillis("2026-03-08", "America/New_York")
        val end = parseLocalToolTimeEpochMillis("2026-03-09", "America/New_York")
        assertEquals(23 * 3600000L, end - start)
    }
}

internal suspend fun Tool.result(args: String): JsonObject = Json.parseToJsonElement(
    (execute(Json.parseToJsonElement(args)).single() as UIMessagePart.Text).text,
).jsonObject

private class FakeCalendar : CalendarAccess {
    var allowed = true
    var events = emptyList<CalendarEvent>()
    var queryArgs: List<Any?>? = null
    var created: CalendarEventDraft? = null
    var calendarId: String? = "calendar"
    var eventId: JsonPrimitive? = JsonPrimitive(123)
    var failure: Exception? = null
    override fun hasReadPermission() = allowed
    override fun hasWritePermission() = allowed
    override suspend fun requestPermission() = allowed
    override suspend fun query(startMs: Long, endMs: Long, query: String?, limit: Int): List<CalendarEvent> {
        failure?.let { throw it }
        queryArgs = listOf(startMs, endMs, query, limit)
        return events
    }
    override suspend fun defaultCalendarId() = calendarId
    override suspend fun create(calendarId: String, event: CalendarEventDraft): JsonPrimitive? {
        created = event
        return eventId
    }
}
