package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ScreenTimeToolTest {
    @Test
    fun sortsFiltersAndTotalsBeforeApplyingTop() = runTest {
        val access = FakeUsage().apply {
            apps = listOf(AppUsage("a", "A", 60000), AppUsage("c", "C", 0), AppUsage("b", "B", 180000))
        }
        val tool = buildScreenTimeTool(access)
        assertEquals("get_screen_time", tool.name)
        val result = tool.result("""{"begin":"1000","end":"2000","top":1}""")
        assertEquals(JsonPrimitive(240000L), result["total_ms"])
        assertEquals(JsonPrimitive(4), result["total_minutes"])
        assertEquals(JsonPrimitive("custom"), result["range"])
        assertEquals(JsonPrimitive("b"), result.getValue("apps").jsonArray.single().jsonObject["package"])
        assertEquals(1000L to 2000L, access.range)
    }

    @Test
    fun permissionErrorDoesNotReadUsage() = runTest {
        val access = FakeUsage().apply { permissionError = LocalToolAccessException("NO_PERMISSION", "Allow data access") }
        val result = buildScreenTimeTool(access).result("{}")
        assertEquals(JsonPrimitive("NO_PERMISSION"), result["error"])
        assertFalse(access.queried)
    }

    @Test
    fun nativeRegionalOrDataErrorsAreNotReportedAsZeroUsage() = runTest {
        for (code in listOf("UNAVAILABLE", "NO_DATA", "NO_PERMISSION")) {
            val access = FakeUsage().apply { queryError = LocalToolAccessException(code, "Native reason") }
            assertEquals(JsonPrimitive(code), buildScreenTimeTool(access)
                .result("""{"begin":"1000","end":"2000"}""")["error"])
        }
    }

    @Test
    fun invalidRangeDoesNotReadUsage() = runTest {
        val access = FakeUsage()
        val result = buildScreenTimeTool(access).result("""{"begin":"2000","end":"1000"}""")
        assertEquals("INVALID_RANGE", result["error"]?.jsonPrimitive?.content)
        assertFalse(access.queried)
    }

    @Test
    fun cancellationIsNotConvertedToToolError() = runTest {
        val access = FakeUsage().apply { queryError = CancellationException("cancelled") }
        assertFailsWith<CancellationException> {
            buildScreenTimeTool(access).result("""{"begin":"1000","end":"2000"}""")
        }
    }
}

private class FakeUsage : ScreenTimeAccess {
    var apps = emptyList<AppUsage>()
    var permissionError: Exception? = null
    var queryError: Exception? = null
    var queried = false
    var range: Pair<Long, Long>? = null
    override suspend fun checkPermission() { permissionError?.let { throw it } }
    override suspend fun requestPermission() = permissionError == null
    override suspend fun query(startMs: Long, endMs: Long): List<AppUsage> {
        queried = true
        range = startMs to endMs
        queryError?.let { throw it }
        return apps
    }
}
