package me.rerere.rikkahub.web

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.service.ChatServiceTestFixture
import me.rerere.rikkahub.service.RecordingChatProvider
import me.rerere.rikkahub.service.response
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.data.ai.transformers.DocumentTextExtractor
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.ServerSocket
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class WebApiTest {
    @Test
    fun `JWT retains password checks token sources and rotation`() = scenario(jwt = true) { api, f, _ ->
        assertEquals(401, api.request("GET", "/api/conversations").status)
        assertEquals(401, api.request("POST", "/api/auth/token", """{"password":"wrong"}""").status)
        val login = api.request("POST", "/api/auth/token", """{"password":"CMP77-password"}""")
        assertEquals(200, login.status)
        val token = login.json["token"]!!.jsonPrimitive.content
        val expiresAt = login.json["expiresAt"]!!.jsonPrimitive.content.toLong()
        assertTrue(expiresAt - System.currentTimeMillis() in (29L * 86400000)..(30L * 86400000))
        assertEquals(200, api.request("GET", "/api/conversations", token = token).status)
        assertEquals(200, api.request("GET", "/api/conversations?access_token=$token").status)
        f.settings.update { it.copy(webServerAccessPassword = "rotated") }
        assertEquals(401, api.request("GET", "/api/conversations", token = token).status)
        f.settings.update { it.copy(webServerAccessPassword = "") }
        assertEquals(403, api.request("GET", "/api/conversations", token = token).status)
        assertEquals(400, api.request("POST", "/api/auth/token", """{"password":""}""").status)
        assertEquals(200, api.request("GET", "/api/ai-icon?name=OpenAI").status)
    }

    @Test
    fun `conversation folder and assistant routes save through the original repositories`() = scenario { api, f, _ ->
        val settings = f.settings.settingsFlow.value
        val assistant = settings.assistants.first { it.id == settings.assistantId }
        val conversation = Conversation.ofId(Uuid.random()).copy(assistantId = assistant.id, title = "Original")
        f.load(conversation)
        val path = "/api/conversations/${conversation.id}"
        assertEquals(1, api.request("GET", "/api/conversations").element.jsonArray.size)
        assertEquals(400, api.request("GET", "/api/conversations/paged?limit=0").status)
        assertEquals(1, api.request("GET", "/api/conversations/paged").json["items"]!!.jsonArray.size)
        assertEquals(400, api.request("POST", "$path/title", """{"title":"  "}""").status)
        assertEquals(200, api.request("POST", "$path/title", """{"title":"  CMP77 中文  "}""").status)
        assertEquals("CMP77 中文", f.repository.getConversationById(conversation.id)!!.title)
        assertEquals(200, api.request("POST", "$path/pin").status)
        assertTrue(f.repository.getConversationById(conversation.id)!!.isPinned)
        val folder = api.request("POST", "/api/folders", """{"name":"  CMP77 Folder  "}""")
        assertEquals(201, folder.status)
        val folderId = folder.json["id"]!!.jsonPrimitive.content
        assertEquals("CMP77 Folder", folder.json["name"]!!.jsonPrimitive.content)
        assertEquals(200, api.request("POST", "$path/folder", """{"folderId":"$folderId"}""").status)
        assertEquals(Uuid.parse(folderId), f.repository.getConversationById(conversation.id)!!.folderId)
        assertEquals(204, api.request("DELETE", "/api/folders/$folderId").status)
        assertEquals(null, f.repository.getConversationById(conversation.id)!!.folderId)
        val other = Assistant(name = "Other")
        f.settings.update { it.copy(assistants = it.assistants + other) }
        assertEquals(200, api.request("POST", "/api/settings/assistant", """{"assistantId":"${other.id}"}""").status)
        assertEquals(other.id, f.settings.settingsFlow.value.assistantId)
        assertTrue(api.request("GET", "/api/conversations").element.jsonArray.isEmpty())
        assertEquals(204, api.request("DELETE", path).status)
        assertEquals(404, api.request("GET", path).status)
    }

    @Test
    fun `file upload download deletion and resource reads retain bytes names and errors`() = scenario { api, f, _ ->
        val bytes = "CMP77 中文\nunchanged".encodeToByteArray()
        val uploaded = api.upload("folder/CMP77-note.txt", bytes)
        assertEquals(201, uploaded.status)
        val file = uploaded.json["files"]!!.jsonArray.single().jsonObject
        val id = file["id"]!!.jsonPrimitive.content.toLong()
        assertEquals("CMP77-note.txt", file["fileName"]!!.jsonPrimitive.content)
        val entity = assertNotNull(f.filesManager.get(id))
        assertEquals(bytes.size.toLong(), entity.sizeBytes)
        assertTrue(file["url"]!!.jsonPrimitive.content.startsWith("file:///"))
        assertContentEquals(bytes, api.request("GET", "/api/files/id/$id").bytes)
        val download = api.request("GET", "/api/files/path/${entity.relativePath}")
        assertContentEquals(bytes, download.bytes)
        assertTrue(download.contentType.startsWith("text/plain"))
        assertEquals(400, api.request("GET", "/api/files/path/../outside").status)
        assertEquals(400, api.upload("empty.txt", byteArrayOf()).status)
        assertEquals(400, api.upload("large.txt", ByteArray(20 * 1024 * 1024 + 1)).status)
        assertEquals(200, api.request("DELETE", "/api/files/$id").status)
        assertEquals(null, f.filesManager.get(id))
        assertFalse(f.filesDir.resolve(entity.relativePath).exists())
        assertEquals(404, api.request("GET", "/api/files/id/$id").status)
        val icon = api.request("GET", "/api/ai-icon?name=OpenAI")
        val asset = api.request("GET", "/api/assets/icons/openai.svg")
        assertEquals(200, asset.status)
        assertContentEquals(icon.bytes, asset.bytes)
        assertEquals(404, api.request("GET", "/api/assets/missing.txt").status)
        assertTrue(api.request("GET", "/").bytes.decodeToString().contains("<html"))
    }

    @Test
    fun `SSE publishes settings and replies while saving the conversation`() = scenario { api, f, p ->
        api.connection("/api/events").let { connection ->
            try {
                connection.inputStream.bufferedReader().use { reader ->
                    val events = generateSequence { reader.event() }.take(3).toList()
                    assertEquals(
                        setOf("settings", "folders", "conversation_list_invalidate"),
                        events.map { it.first }.toSet(),
                    )
                    val settings = JsonInstant.parseToJsonElement(
                        events.first { it.first == "settings" }.second,
                    ).jsonObject
                    assertEquals(
                        f.settings.settingsFlow.value.assistantId.toString(),
                        settings["assistantId"]!!.jsonPrimitive.content,
                    )
                }
            } finally { connection.disconnect() }
        }
        val conversation = Conversation.ofId(Uuid.random()).copy(
            assistantId = f.settings.settingsFlow.value.assistantId, title = "Keep title",
        )
        f.load(conversation)
        p.stream = { flow {
            emit(response("CMP77 ", delta = true))
            delay(150)
            emit(response("completed", delta = true))
        } }
        api.connection("/api/conversations/${conversation.id}/stream").let { connection ->
            try {
                connection.inputStream.bufferedReader().use { reader ->
                    assertEquals("snapshot", reader.event().first)
                    val sent = api.request("POST", "/api/conversations/${conversation.id}/messages",
                        """{"parts":[{"type":"text","text":"CMP77 question"}]}""")
                    assertEquals(202, sent.status)
                    var sawReply = false
                    var finished = false
                    repeat(20) {
                        if (finished) return@repeat
                        val event = reader.event()
                        assertTrue(event.first in setOf("snapshot", "node_update"), event.toString())
                        sawReply = sawReply || "CMP77 completed" in event.second
                        finished = sawReply && "\"isGenerating\":false" in event.second
                    }
                    assertTrue(finished)
                }
            } finally { connection.disconnect() }
        }
        withTimeout(5.seconds) {
            while (f.repository.getConversationById(conversation.id)
                    ?.currentMessages?.lastOrNull()?.toText() != "CMP77 completed") {
                delay(10)
            }
        }
        assertTrue(p.calls.first().messages.any { it.toText() == "CMP77 question" })
    }

    private fun scenario(
        jwt: Boolean = false,
        block: suspend (Api, ChatServiceTestFixture, RecordingChatProvider) -> Unit,
    ) = runBlocking {
        val provider = RecordingChatProvider()
        ChatServiceTestFixture(provider).use { fixture ->
            startKoin { modules(module {
                single { fixture.settings }
                single { fixture.filesManager }
                single<DocumentTextExtractor> { DocumentTextExtractor { _, _ -> error("Unexpected document") } }
            }) }
            val model = Model("cmp77", "CMP77")
            val assistant = Assistant(name = "CMP77", chatModelId = model.id)
            fixture.configure(Settings(
                providers = listOf(ProviderSetting.OpenAI(models = listOf(model))),
                assistants = listOf(assistant), assistantId = assistant.id, chatModelId = model.id,
                enableSuggestion = false, webServerJwtEnabled = jwt, webServerAccessPassword = "CMP77-password",
            ))
            val runtime = createJvmWebServerRuntime(fixture.scope, fixture.filesDir, fixture.service,
                fixture.repository, fixture.folderRepository, fixture.settings, fixture.filesManager)
            val port = ServerSocket(0).use { it.localPort }
            try {
                runtime.start(port, localhostOnly = true)
                withTimeout(5.seconds) { runtime.state.first { it.isRunning || it.error != null } }
                assertEquals(null, runtime.state.value.error)
                withTimeout(30.seconds) { block(Api(port), fixture, provider) }
            } finally {
                runtime.stop()
                withTimeout(5.seconds) { runtime.state.first { !it.isLoading && !it.isRunning } }
                stopKoin()
            }
        }
    }

    private class Api(private val port: Int) {
        fun connection(path: String) = (URI("http://127.0.0.1:$port$path").toURL()
            .openConnection(Proxy.NO_PROXY) as HttpURLConnection).apply {
            connectTimeout = 3000
            readTimeout = 5000
        }

        fun request(method: String, path: String, body: String? = null, token: String? = null): Reply =
            request(method, path, body?.encodeToByteArray(), "application/json", token)

        fun upload(name: String, bytes: ByteArray): Reply = request("POST", "/api/files/upload",
            ("--CMP77\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$name\"\r\n" +
                "Content-Type: text/plain\r\n\r\n").encodeToByteArray() + bytes +
                "\r\n--CMP77--\r\n".encodeToByteArray(),
            "multipart/form-data; boundary=CMP77")

        private fun request(
            method: String, path: String, body: ByteArray?, type: String, token: String? = null,
        ): Reply {
            val connection = connection(path)
            return try {
                connection.requestMethod = method
                token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
                body?.let {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", type)
                    connection.outputStream.use { output -> output.write(it) }
                }
                val status = connection.responseCode
                val stream = if (status >= 400) connection.errorStream else connection.inputStream
                Reply(status, stream?.use { it.readBytes() } ?: byteArrayOf(), connection.contentType.orEmpty())
            } finally { connection.disconnect() }
        }
    }

    private data class Reply(val status: Int, val bytes: ByteArray, val contentType: String) {
        val element: JsonElement get() = JsonInstant.parseToJsonElement(bytes.decodeToString())
        val json get() = element.jsonObject
    }

    private fun BufferedReader.event(): Pair<String, String> {
        var name = ""
        var data = ""
        while (true) {
            val line = readLine() ?: error("SSE closed before event")
            when {
                line.startsWith("event:") -> name = line.substringAfter(':').trim()
                line.startsWith("data:") -> data = line.substringAfter(':').trim()
                line.isEmpty() && name.isNotEmpty() -> return name to data
            }
        }
    }
}
