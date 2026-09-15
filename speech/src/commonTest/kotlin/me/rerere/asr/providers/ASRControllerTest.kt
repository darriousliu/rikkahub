package me.rerere.asr.providers

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.io.readIntLe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.ASRStatus
import me.rerere.asr.Microphone
import me.rerere.asr.PcmRecorder
import kotlin.io.encoding.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ASRControllerTest {
    @BeforeTest fun setup() { Dispatchers.setMain(Dispatchers.Unconfined) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    @Test
    fun openAiKeepsSessionPcmAndIncrementalTranscripts() = runTest {
        withContext(Dispatchers.Default) {
            val mic = FakeMicrophone()
            val ws = FakeTransport()
            val provider = ASRProviderSetting.OpenAIRealtime(apiKey = "test", language = "zh", prompt = "names")
            val controller = OpenAIRealtimeASRController(mic, ws, provider)
            try {
                val transcripts = Channel<String>(Channel.UNLIMITED)
                controller.start { transcripts.trySend(it) }
                assertEquals(ASRStatus.Connecting, controller.state.value.status)
                assertEquals("Bearer test", ws.headers["Authorization"])
                assertTrue(ws.url.endsWith("intent=transcription"))
                ws.open()
                val update = parse(ws.texts.receive())
                assertEquals(parse("""
                    {
                      "type": "session.update",
                      "session": {
                        "type": "transcription",
                        "audio": {
                          "input": {
                            "format": {
                              "type": "audio/pcm",
                              "rate": 24000
                            },
                            "transcription": {
                              "model": "gpt-4o-transcribe",
                              "language": "zh",
                              "prompt": "names"
                            },
                            "noise_reduction": {
                              "type": "near_field"
                            },
                            "turn_detection": {
                              "type": "server_vad",
                              "threshold": 0.5,
                              "prefix_padding_ms": 300,
                              "silence_duration_ms": 500
                            }
                          }
                        }
                      }
                    }
                """), update)
                mic.awaitRead()
                assertEquals(24000, mic.sampleRate)
                assertEquals(4800, mic.minBufferSize)
                val pcm = byteArrayOf(0, 0, 0, 64, 0, -64)
                mic.recorder.frames.send(pcm)
                val audio = parse(ws.texts.receive())
                assertEquals("input_audio_buffer.append", audio.optString("type"))
                assertContentEquals(pcm, Base64.decode(audio.optString("audio")))
                ws.text("""{"type":"conversation.item.input_audio_transcription.delta","delta":"你"}""")
                assertEquals("你", transcripts.receive())
                ws.text("""{"type":"conversation.item.input_audio_transcription.delta","delta":"好"}""")
                assertEquals("你好", transcripts.receive())
                ws.text("""{"type":"conversation.item.input_audio_transcription.completed","transcript":"  你好。  "}""")
                assertEquals("你好。", transcripts.receive())
                ws.text("""{"type":"conversation.item.input_audio_transcription.delta","item_id":"second","delta":"世界"}""")
                assertEquals("你好。 世界", transcripts.receive())
                ws.text("invalid json")
                assertEquals("你好。 世界", controller.state.value.transcript)
                assertTrue(controller.state.value.amplitudes.isNotEmpty())
                controller.stop()
                assertEquals(1000 to "stop", withTimeout(2000) { ws.closed.await() })
                assertEquals(ASRStatus.Idle, controller.state.value.status)
                assertTrue(mic.recorder.released)
            } finally { controller.dispose() }
        }
    }

    @Test
    fun dashScopeKeepsTextReplacementNullVadAndQueueLimit() = runTest {
        withContext(Dispatchers.Default) {
            val mic = FakeMicrophone()
            val ws = FakeTransport()
            val provider = ASRProviderSetting.DashScope(apiKey = "test", language = "zh", vadThreshold = 0f)
            val controller = DashScopeASRController(mic, ws, provider)
            try {
                controller.start {}
                assertEquals("realtime=v1", ws.headers["OpenAI-Beta"])
                assertTrue(ws.url.endsWith("model=${provider.model}"))
                ws.open()
                val update = parse(ws.texts.receive())
                assertEquals(parse("""
                    {
                      "event_id": "evt_session_update",
                      "type": "session.update",
                      "session": {
                        "modalities": [
                          "text"
                        ],
                        "input_audio_format": "pcm",
                        "sample_rate": 16000,
                        "input_audio_transcription": {
                          "language": "zh"
                        },
                        "turn_detection": null
                      }
                    }
                """), update)
                mic.awaitRead()
                ws.queueSize = 100_000
                mic.recorder.frames.send(ByteArray(128))
                mic.awaitRead()
                assertTrue(ws.texts.tryReceive().isFailure, "Full websocket queues still drop audio frames")
                ws.queueSize = 0
                mic.recorder.frames.send(byteArrayOf(1, 2, 3, 4))
                val audio = parse(ws.texts.receive())
                assertTrue(audio.optString("event_id").startsWith("evt_"))
                assertContentEquals(byteArrayOf(1, 2, 3, 4), Base64.decode(audio.optString("audio")))
                ws.text("""{"type":"conversation.item.input_audio_transcription.delta","delta":"part"}""")
                ws.text("""{"type":"conversation.item.input_audio_transcription.text","text":"replacement"}""")
                assertEquals("replacement", controller.state.value.transcript)
                ws.text("""{"type":"conversation.item.input_audio_transcription.completed","transcript":"final"}""")
                assertEquals("final", controller.state.value.transcript)
                ws.text("""{"type":"error","error":{"message":"provider rejected audio"}}""")
                assertEquals(ASRStatus.Error, controller.state.value.status)
                assertEquals("provider rejected audio", controller.state.value.errorMessage)
            } finally { controller.dispose() }
        }
    }

    @Test
    fun volcengineKeepsGzipHandshakeBinaryAudioAndLastPacket() = runTest {
        withContext(Dispatchers.Default) {
            val mic = FakeMicrophone()
            val ws = FakeTransport()
            val controller = VolcengineASRController(mic, ws, ASRProviderSetting.Volcengine(apiKey = "test"))
            try {
                val transcripts = Channel<String>(Channel.UNLIMITED)
                controller.start { transcripts.trySend(it) }
                assertEquals("test", ws.headers["X-Api-Key"])
                assertEquals("-1", ws.headers["X-Api-Sequence"])
                ws.open()
                val frame = ws.bytes.receive()
                assertContentEquals(byteArrayOf(0x11, 0x10, 0x11, 0), frame.copyOfRange(0, 4))
                assertEquals(parse("""
                    {
                      "user": {
                        "uid": "rikkahub"
                      },
                      "audio": {
                        "format": "pcm",
                        "rate": 16000,
                        "bits": 16,
                        "channel": 1
                      },
                      "request": {
                        "model_name": "bigmodel",
                        "enable_itn": true,
                        "enable_punc": true,
                        "show_utterances": true,
                        "result_type": "full"
                      }
                    }
                """), parse(gzipDecompress(frame.copyOfRange(8, frame.size)).decodeToString()))
                mic.awaitRead()
                assertEquals(6400, mic.minBufferSize)
                val pcm = ByteArray(6400) { (it % 127).toByte() }
                mic.recorder.frames.send(pcm)
                assertContentEquals(VolcengineFrameCodec.buildFrame(2, 0, 0, 0, pcm), ws.bytes.receive())
                val result = VolcengineFrameCodec.buildFrame(9, 0, 1, 1, gzipCompress("""{"result":{"text":"你好"}}""".encodeToByteArray()))
                ws.listener.onBinary(ws, result)
                assertEquals("你好", transcripts.receive())
                ws.listener.onBinary(ws, result)
                assertTrue(transcripts.tryReceive().isFailure, "Repeated full transcripts are ignored")
                controller.stop()
                assertContentEquals(byteArrayOf(0x11, 0x22, 0, 0, 0, 0, 0, 0), ws.bytes.receive())
                withTimeout(2500) { ws.closed.await() }
            } finally { controller.dispose() }
        }
    }

    @Test
    fun mimoFlushesRemainingPcmAsWavAndKeepsLanguageAndHeaders() = runTest {
        withContext(Dispatchers.Default) {
            val mic = FakeMicrophone()
            val requests = Channel<JsonObject>(Channel.UNLIMITED)
            val client = HttpClient(MockEngine { request ->
                assertEquals("https://mimo.test/v1/chat/completions", request.url.toString())
                assertEquals("test", request.headers["api-key"])
                requests.send(parse((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()))
                respond("""{"choices":[{"message":{"content":"  识别完成  "}}]}""", HttpStatusCode.OK)
            })
            val controller = MiMoASRController(mic, client, ASRProviderSetting.MiMo(apiKey = "test", baseUrl = "https://mimo.test/v1/", language = "zh"))
            try {
                val transcript = CompletableDeferred<String>()
                controller.start { transcript.complete(it) }
                mic.awaitRead()
                val pcm = ByteArray(4000) { (it % 127).toByte() }
                mic.recorder.frames.send(pcm)
                mic.awaitRead()
                controller.stop()
                val body = requests.receive()
                val b64 = body["messages"]!!.jsonArray[0].jsonObject["content"]!!
                    .jsonArray[0].jsonObject["input_audio"]!!.jsonObject.optString("data")
                val wav = Base64.decode(b64.removePrefix("data:audio/wav;base64,"))
                assertEquals("RIFF", wav.copyOfRange(0, 4).decodeToString())
                assertEquals("WAVE", wav.copyOfRange(8, 12).decodeToString())
                assertEquals(pcm.size + 36, Buffer().apply { write(wav, 4, 8) }.readIntLe())
                assertEquals(16000, Buffer().apply { write(wav, 24, 28) }.readIntLe())
                assertContentEquals(pcm, wav.copyOfRange(44, wav.size))
                assertEquals("zh", body["asr_options"]!!.jsonObject.optString("language"))
                assertEquals("识别完成", transcript.await())
                controller.state.first { it.status == ASRStatus.Idle }
                assertTrue(mic.recorder.released)
            } finally { controller.dispose(); client.close() }
        }
    }

    @Test
    fun stepKeepsRawPcmOptionsAndSseFinalText() = runTest {
        withContext(Dispatchers.Default) {
            val mic = FakeMicrophone()
            val pcm = ByteArray(4000) { (it % 127).toByte() }
            val client = HttpClient(MockEngine { request ->
                assertEquals("https://step.test/v1/audio/asr/sse", request.url.toString())
                assertEquals("Bearer test", request.headers["Authorization"])
                val body = parse((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                val audio = body["audio"]!!.jsonObject
                assertContentEquals(pcm, Base64.decode(audio.optString("data")))
                assertEquals(parse("""{"type":"pcm","codec":"pcm_s16le","rate":16000,"bits":16,"channel":1}"""), audio["input"]!!.jsonObject["format"])
                val transcription = audio["input"]!!.jsonObject["transcription"]!!.jsonObject
                assertEquals("false", transcription.optString("enable_itn"))
                assertEquals("true", transcription.optString("enable_timestamp"))
                assertEquals("RikkaHub", transcription["hotwords"]!!.jsonArray[0].jsonPrimitive.content)
                respond("event: transcript.text.delta\ndata: {\"delta\":\"partial\"}\n\nevent: transcript.text.done\ndata: {\"text\":\"最终结果\"}\n\n", HttpStatusCode.OK, headersOf("Content-Type", "text/event-stream"))
            })
            val controller = StepASRController(mic, client, ASRProviderSetting.Step(apiKey = "test", baseUrl = "https://step.test", enableItn = false, enableTimestamp = true, hotwords = listOf("RikkaHub")))
            try {
                val transcript = CompletableDeferred<String>()
                controller.start { transcript.complete(it) }
                mic.awaitRead()
                mic.recorder.frames.send(pcm)
                mic.awaitRead()
                controller.stop()
                assertEquals("最终结果", transcript.await())
                controller.state.first { it.status == ASRStatus.Idle }
            } finally { controller.dispose(); client.close() }
        }
    }

    @Test
    fun mimoStopWaitsForInFlightSegmentBeforeUploadingTail() = runTest {
        withContext(Dispatchers.Default) {
            val mic = FakeMicrophone(recorder = FakeRecorder(bufferSize = 6 * 1024 * 1024))
            val firstStarted = CompletableDeferred<Unit>()
            val finishFirst = CompletableDeferred<Unit>()
            val transcripts = Channel<String>(Channel.UNLIMITED)
            var requestCount = 0
            val client = HttpClient(MockEngine { request ->
                val body = parse((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                val b64 = body["messages"]!!.jsonArray[0].jsonObject["content"]!!
                    .jsonArray[0].jsonObject["input_audio"]!!.jsonObject.optString("data")
                val wav = Base64.decode(b64.substringAfter(','))
                requestCount++
                if (requestCount == 1) {
                    assertEquals(6 * 1024 * 1024 + 44, wav.size)
                    firstStarted.complete(Unit)
                    finishFirst.await()
                } else {
                    assertEquals(4000 + 44, wav.size)
                    assertEquals(2, wav[44].toInt())
                }
                respond("""{"choices":[{"message":{"content":"segment $requestCount"}}]}""", HttpStatusCode.OK)
            })
            val controller = MiMoASRController(mic, client, ASRProviderSetting.MiMo(apiKey = "test"))
            try {
                controller.start { transcripts.trySend(it) }
                mic.awaitRead()
                mic.recorder.frames.send(ByteArray(6 * 1024 * 1024) { 1 })
                firstStarted.await()
                mic.awaitRead()
                mic.recorder.frames.send(ByteArray(4000) { 2 })
                mic.awaitRead()
                controller.stop()
                assertEquals(ASRStatus.Stopping, controller.state.value.status)
                assertEquals(1, requestCount)
                finishFirst.complete(Unit)
                assertEquals("segment 1", transcripts.receive())
                assertEquals("segment 1 segment 2", transcripts.receive())
                controller.state.first { it.status == ASRStatus.Idle }
                assertEquals(2, requestCount)
            } finally { controller.dispose(); client.close() }
        }
    }

    @Test
    fun allProvidersRejectMissingPermissionBeforeOpeningMicOrNetwork() {
        val mic = FakeMicrophone(permission = false)
        val ws = FakeTransport()
        val client = HttpClient(MockEngine { error("Must not send requests") })
        val controllers = listOf(
            OpenAIRealtimeASRController(mic, ws, ASRProviderSetting.OpenAIRealtime()),
            DashScopeASRController(mic, ws, ASRProviderSetting.DashScope()),
            VolcengineASRController(mic, ws, ASRProviderSetting.Volcengine()),
            MiMoASRController(mic, client, ASRProviderSetting.MiMo()),
            StepASRController(mic, client, ASRProviderSetting.Step()),
        )
        try {
            controllers.forEach {
                it.start { error("Must not return transcripts") }
                assertEquals(ASRStatus.Error, it.state.value.status)
                assertEquals("Microphone permission is required", it.state.value.errorMessage)
            }
            assertFalse(mic.created.isCompleted)
            assertEquals("", ws.url)
        } finally { controllers.forEach { it.dispose() }; client.close() }
    }

    @Test
    fun stepDropsOriginalSub3200ByteTail() = runTest {
        withContext(Dispatchers.Default) {
            val mic = FakeMicrophone()
            val client = HttpClient(MockEngine { error("Short tail must not be uploaded") })
            val controller = StepASRController(mic, client, ASRProviderSetting.Step(apiKey = "test"))
            try {
                controller.start { error("Must not return transcripts") }
                mic.awaitRead()
                mic.recorder.frames.send(ByteArray(3198))
                mic.awaitRead()
                controller.stop()
                controller.state.first { it.status == ASRStatus.Idle }
                assertEquals("", controller.state.value.transcript)
            } finally { controller.dispose(); client.close() }
        }
    }

    private class FakeMicrophone(
        private val permission: Boolean = true,
        val recorder: FakeRecorder = FakeRecorder(),
    ) : Microphone {
        val created = CompletableDeferred<Unit>()
        var sampleRate = 0
        var minBufferSize = 0
        override val hasPermission get() = permission
        override fun requestAudioFocus() = true
        override fun abandonAudioFocus() = Unit
        override fun createRecorder(sampleRate: Int, minBufferSize: Int): PcmRecorder {
            this.sampleRate = sampleRate
            this.minBufferSize = minBufferSize
            created.complete(Unit)
            return recorder
        }
        suspend fun awaitRead() = withTimeout(3000) { recorder.reads.receive() }
    }

    private class FakeRecorder(override val bufferSize: Int = 6400) : PcmRecorder {
        val frames = Channel<ByteArray>(Channel.UNLIMITED)
        val reads = Channel<Unit>(Channel.UNLIMITED)
        var released = false
        override fun startRecording() = Unit
        override suspend fun read(buffer: ByteArray, offset: Int, size: Int): Int {
            reads.send(Unit)
            val frame = frames.receive()
            frame.copyInto(buffer, offset)
            return frame.size
        }
        override fun stop() { frames.cancel(CancellationException("Stopped")) }
        override fun release() { released = true }
    }

    private class FakeTransport : AsrWebSocketTransport, AsrWebSocketSession {
        lateinit var listener: AsrWebSocketListener
        var url = ""
        var headers = emptyMap<String, String>()
        val texts = Channel<String>(Channel.UNLIMITED)
        val bytes = Channel<ByteArray>(Channel.UNLIMITED)
        val closed = CompletableDeferred<Pair<Int, String>>()
        override var queueSize = 0L
        override fun connect(url: String, headers: Map<String, String>, listener: AsrWebSocketListener): AsrWebSocketSession {
            this.url = url; this.headers = headers; this.listener = listener
            return this
        }
        override fun close() = Unit
        fun open() = listener.onOpen(this)
        fun text(text: String) = listener.onText(this, text)
        override fun send(text: String) = texts.trySend(text).isSuccess
        override fun send(bytes: ByteArray) = this.bytes.trySend(bytes).isSuccess
        override fun close(code: Int, reason: String): Boolean {
            listener.onClosed(this, code, reason)
            return closed.complete(code to reason)
        }
    }
}

private fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject
