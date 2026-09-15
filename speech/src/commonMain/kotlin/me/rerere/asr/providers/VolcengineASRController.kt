package me.rerere.asr.providers

import me.rerere.common.logging.RikkaLog as Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.rerere.asr.Microphone
import me.rerere.asr.PcmRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.IO
import me.rerere.asr.ASRController
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.ASRState
import me.rerere.asr.ASRStatus
import me.rerere.asr.appendAmplitude
import me.rerere.asr.calculateRmsAmplitude
import kotlin.uuid.Uuid

private const val TAG = "VolcengineASR"
private const val MAX_WEBSOCKET_QUEUE_BYTES = 100_000L

class VolcengineASRController(
    private val microphone: Microphone,
    private val webSocketTransport: AsrWebSocketTransport,
    private val provider: ASRProviderSetting.Volcengine
) : ASRController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(ASRState(isAvailable = true))
    override val state: StateFlow<ASRState> = _state.asStateFlow()

    private var webSocket: AsrWebSocketSession? = null
    private var recorderJob: Job? = null
    private var audioRecord: PcmRecorder? = null
    private var onTranscriptChange: ((String) -> Unit)? = null
    private var lastText = ""

    override fun start(onTranscriptChange: (String) -> Unit) {
        if (state.value.isRecording) return
        if (!microphone.hasPermission) {
            setError("Microphone permission is required")
            return
        }

        this.onTranscriptChange = onTranscriptChange
        lastText = ""
        _state.update {
            ASRState(
                status = ASRStatus.Connecting,
                isAvailable = true
            )
        }

        webSocket = webSocketTransport.connect(
            url = provider.websocketUrl,
            headers = mapOf(
                "X-Api-Key" to provider.apiKey,
                "X-Api-Resource-Id" to provider.resourceId,
                "X-Api-Request-Id" to Uuid.random().toString(),
                "X-Api-Sequence" to "-1",
            ),
            listener = object : AsrWebSocketListener {
            override fun onOpen(session: AsrWebSocketSession) {
                val payload = buildFullClientRequestPayload()
                val compressed = gzipCompress(payload)
                val frame = VolcengineFrameCodec.buildFrame(
                    messageType = MSG_FULL_CLIENT_REQUEST,
                    flags = 0x00,
                    serialization = SER_JSON,
                    compression = COMP_GZIP,
                    payload = compressed
                )
                session.send(frame)
                _state.update { it.copy(status = ASRStatus.Listening, errorMessage = null) }
                startRecorder(session)
            }

            override fun onText(session: AsrWebSocketSession, text: String) = Unit

            override fun onBinary(session: AsrWebSocketSession, bytes: ByteArray) {
                handleBinaryResponse(bytes)
            }

            override fun onFailure(session: AsrWebSocketSession, error: Throwable) {
                Log.e(TAG, "Volcengine ASR websocket failed", error)
                releaseRecorder()
                setError(error.message ?: "ASR websocket failed")
            }

            override fun onClosed(session: AsrWebSocketSession, code: Int, reason: String) {
                releaseRecorder()
                _state.update { it.copy(status = ASRStatus.Idle, errorMessage = null) }
            }
        })
    }

    override fun stop() {
        recorderJob?.cancel()
        releaseRecorder()
        val socket = webSocket
        if (socket != null) {
            _state.update { it.copy(status = ASRStatus.Stopping) }
            val lastFrame = VolcengineFrameCodec.buildFrame(
                messageType = MSG_AUDIO_ONLY,
                flags = FLAG_LAST_PACKET,
                serialization = SER_NONE,
                compression = COMP_NONE,
                payload = ByteArray(0)
            )
            socket.send(lastFrame)
            scope.launch {
                delay(1000)
                socket.close(1000, "stop")
                if (webSocket === socket) {
                    webSocket = null
                    _state.update { it.copy(status = ASRStatus.Idle) }
                }
            }
        } else {
            _state.update { it.copy(status = ASRStatus.Idle) }
        }
    }

    override fun dispose() {
        stop()
        scope.cancel()
    }

    private fun buildFullClientRequestPayload(): ByteArray {
        val json = buildJsonObject {
            put("user", buildJsonObject { put("uid", "rikkahub") })
            put("audio", buildJsonObject {
                put("format", "pcm")
                put("rate", SAMPLE_RATE)
                put("bits", 16)
                put("channel", 1)
                if (provider.language.isNotBlank()) put("language", provider.language)
            })
            put("request", buildJsonObject {
                put("model_name", "bigmodel")
                put("enable_itn", true)
                put("enable_punc", true)
                put("show_utterances", true)
                put("result_type", "full")
            })
        }
        return json.toString().encodeToByteArray()
    }

    private fun handleBinaryResponse(data: ByteArray) {
        when (val frame = VolcengineFrameCodec.parseResponse(data) ?: return) {
            is VolcengineFrameCodec.ServerFrame.Result -> {
                var payload = frame.payload
                if (frame.compression == COMP_GZIP) {
                    payload = runCatching { gzipDecompress(payload) }.getOrElse {
                        Log.w(TAG, "Gzip decompression failed", it)
                        return
                    }
                }

                val json = runCatching {
                    Json.parseToJsonElement(payload.decodeToString()).jsonObject
                }.getOrElse {
                    Log.w(TAG, "Failed to parse response JSON", it)
                    return
                }

                val text = (json["result"] as? JsonObject)?.optString("text", "") ?: ""
                if (text.isNotEmpty() && text != lastText) {
                    lastText = text
                    _state.update { it.copy(transcript = text, errorMessage = null) }
                    scope.launch { onTranscriptChange?.invoke(text) }
                }
            }

            is VolcengineFrameCodec.ServerFrame.Error -> {
                val errorMsg = frame.message?.decodeToString() ?: "Volcengine ASR error"
                Log.e(TAG, "Volcengine ASR error: $errorMsg")
                setError(errorMsg)
            }

            is VolcengineFrameCodec.ServerFrame.Ignored -> {
                Log.v(TAG, "Ignored message type: ${frame.messageType}")
            }
        }
    }

    private fun startRecorder(socket: AsrWebSocketSession) {
        recorderJob?.cancel()
        recorderJob = scope.launch(Dispatchers.IO) {
            val recorder = microphone.createRecorder(SAMPLE_RATE, SAMPLE_RATE * 2 * 200 / 1000)
            val chunkSize = recorder.bufferSize
            audioRecord = recorder

            try {
                recorder.startRecording()
                val buffer = ByteArray(chunkSize)
                while (isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val amplitude = calculateRmsAmplitude(buffer, read)
                        _state.update { it.copy(amplitudes = it.amplitudes.appendAmplitude(amplitude)) }
                        if (socket.queueSize < MAX_WEBSOCKET_QUEUE_BYTES) {
                            val frame = VolcengineFrameCodec.buildFrame(
                                messageType = MSG_AUDIO_ONLY,
                                flags = 0x00,
                                serialization = SER_NONE,
                                compression = COMP_NONE,
                                payload = buffer.copyOfRange(0, read)
                            )
                            socket.send(frame)
                        } else {
                            Log.w(TAG, "WebSocket queue full, dropping audio frame")
                        }
                    } else if (read < 0) {
                        throw IllegalStateException("AudioRecord read error: $read")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Audio recording failed", e)
                setError(e.message ?: "Audio recording failed")
            } finally {
                releaseRecorder()
            }
        }
    }

    private fun setError(message: String) {
        _state.update { it.copy(status = ASRStatus.Error, errorMessage = message) }
    }

    private fun releaseRecorder() {
        recorderJob = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val MSG_FULL_CLIENT_REQUEST = 0x01
        private const val MSG_AUDIO_ONLY = 0x02
        private const val SER_NONE = 0x00
        private const val SER_JSON = 0x01
        private const val COMP_NONE = 0x00
        private const val COMP_GZIP = 0x01
        private const val FLAG_LAST_PACKET = 0x02

    }
}
