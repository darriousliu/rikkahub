package me.rerere.rikkahub.data.sync.importer

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import me.rerere.rikkahub.data.model.Conversation
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ChatboxStreamingContractTest {
    private val assistantId = Uuid.parse("46000000-0000-4000-8000-000000000001")

    @Test
    fun `stream and collected import retain file order instead of session index order`() = runTest {
        val file = Files.createTempFile("chatbox-stream-", ".json").toFile()
        try {
            file.writeText("""{"settings":{},"chat-sessions-list":[{"id":"b"},{"id":"a"}],
                "session:a":${session("a")},"session:b":${session("b")},"session:a":${session("a")}}""")
            val saved = mutableListOf<Conversation>()
            val result = ChatboxImporter.importStreaming(PlatformFile(file), assistantId, emptyList()) { saved += it }
            assertEquals(listOf("a", "b", "a"), saved.map { it.title })
            assertEquals(3, result.parsedConversations)
            assertEquals(saved.first().id, saved.last().id)
            assertEquals(listOf("a", "b", "a"), ChatboxImporter.import(PlatformFile(file), assistantId,
                emptyList()).conversations.conversations.map { it.title })
        } finally { file.delete() }
    }

    @Test
    fun `provider extraction and cancellation return before a malformed later session`() = runTest {
        val file = Files.createTempFile("chatbox-cancel-", ".json").toFile()
        try {
            file.writeText("""{"settings":{"providers":{"openai":{"apiHost":"https://fixture.invalid",
                "apiKey":"offline-fixture"}}},"session:a":${session("a")},"session:broken":{INVALID""")
            val providers = ChatboxImporter.importProviders(PlatformFile(file))
            assertEquals(1, providers.size)
            var delivered = 0
            val error = assertFailsWith<CancellationException> {
                ChatboxImporter.importStreaming(PlatformFile(file), assistantId, emptyList()) {
                    delivered++
                    throw CancellationException("stop after one")
                }
            }
            assertEquals("stop after one", error.message)
            assertEquals(1, delivered)
            // Closing on exceptional return must leave the FileKit source usable for another read.
            assertEquals(1, ChatboxImporter.importProviders(PlatformFile(file)).size)
        } finally { file.delete() }
    }

    @Test
    fun `parse failure after a valid record preserves the already delivered conversation`() = runTest {
        val file = Files.createTempFile("chatbox-partial-", ".json").toFile()
        try {
            file.writeText("""{"settings":{},"session:a":${session("a")},"session:broken":{"messages":[}""")
            val saved = mutableListOf<Conversation>()
            assertFails {
                ChatboxImporter.importStreaming(PlatformFile(file), assistantId, emptyList()) { saved += it }
            }
            assertEquals(listOf("a"), saved.map { it.title })
            assertTrue(saved.single().messageNodes.isNotEmpty())
        } finally { file.delete() }
    }

    private fun session(id: String) = """{"id":"$id","name":"$id","messages":[
        {"id":"$id-user","role":"user","timestamp":1700000000123,
         "contentParts":[{"type":"text","text":"中文 😀"}]}]}"""
}
