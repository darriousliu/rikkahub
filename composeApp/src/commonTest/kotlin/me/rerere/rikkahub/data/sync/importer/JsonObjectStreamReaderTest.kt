package me.rerere.rikkahub.data.sync.importer

import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.writeString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonObjectStreamReaderTest {
    @Test
    fun `record boundaries preserve unicode escapes quotes nested containers and long integers`() {
        val values = listOf(
            """{"text":"a }, [ b \\\" c \\u4e2d","emoji":"\uD83D\uDE00","long":9223372036854775807}""",
            """[true,false,null,{"中文":"😀"},[0.1,1000.0,-2]]""",
            """"\u4e2dA \uD83D\uDE00 \"\\\n"""",
            "null", "1234567890123456789", "false",
        )
        val input = "{" + values.mapIndexed { index, value -> "\"key$index\":$value" }.joinToString(",") + "}"
        ChunkedSource(input, 3).buffered().use { source ->
            val reader = JsonObjectStreamReader(source)
            values.forEachIndexed { index, value ->
                assertTrue(reader.hasNext())
                assertEquals("key$index", reader.nextName())
                assertEquals(Json.parseToJsonElement(value), reader.nextJsonElement())
            }
            assertFalse(reader.hasNext())
            reader.endObject()
        }
    }

    @Test
    fun `skipped large fields and early return do not read the remaining document`() {
        val input = "{\"first\":1,\"ignored\":\"" + "x".repeat(2_000_000) + "\",\"last\":2}"
        val raw = ChunkedSource(input, 1024)
        raw.buffered().use { source ->
            val reader = JsonObjectStreamReader(source)
            assertEquals("first", reader.nextName())
            assertEquals("1", reader.nextJsonElement().jsonPrimitive.content)
            assertTrue(raw.read < 2048, "The first value must be available before the rest is read")
            assertEquals("ignored", reader.nextName())
            reader.skipValue()
            assertEquals("last", reader.nextName())
            assertEquals("2", reader.nextJsonElement().jsonPrimitive.content)
            reader.endObject()
        }
        assertTrue(raw.closed)
    }

    @Test
    fun `truncated records invalid names and malformed retained values fail`() {
        for (input in listOf("{", "{\"a\":{", "{\"a\":\"unterminated", "{\"a\":[}",
            "{a:1}", "{'a':1}", "{\"a\":{\"x\":}}")) {
            assertFails(input) {
                ChunkedSource(input, 2).buffered().use { source ->
                    val reader = JsonObjectStreamReader(source)
                    while (reader.hasNext()) {
                        reader.nextName()
                        reader.nextJsonElement()
                    }
                    reader.endObject()
                }
            }
        }
    }

    @Test
    fun `numeric tokens retain the original long then double conversion`() {
        ChunkedSource("{\"a\":1e3,\"b\":12345678901234567890,\"c\":1.00}", 3).buffered().use { source ->
            val reader = JsonObjectStreamReader(source)
            assertEquals("a", reader.nextName())
            assertEquals(JsonPrimitive(1000.0), reader.nextJsonElement())
            assertEquals("b", reader.nextName())
            assertEquals(JsonPrimitive("12345678901234567890".toDouble()), reader.nextJsonElement())
            assertEquals("c", reader.nextName())
            assertEquals(JsonPrimitive(1.0), reader.nextJsonElement())
            reader.endObject()
        }
    }

    private class ChunkedSource(input: String, private val chunkSize: Int) : RawSource {
        private val bytes = Buffer().apply { writeString(input) }
        var read = 0L
        var closed = false
        override fun readAtMostTo(sink: Buffer, byteCount: Long): Long =
            bytes.readAtMostTo(sink, minOf(byteCount, chunkSize.toLong())).also { if (it > 0) read += it }
        override fun close() { closed = true }
    }
}
