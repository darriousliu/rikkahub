package me.rerere.asr.providers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import io.ktor.client.HttpClient
import me.rerere.common.logging.RikkaLog as Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.rerere.asr.ASRController
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.ASRState
import me.rerere.asr.ASRStatus
import me.rerere.asr.appendAmplitude
import me.rerere.asr.calculateRmsAmplitude
import java.io.ByteArrayOutputStream

private const val TAG = "StepASR"

// 提前在 6MB 触发分段, 与 MiMo 一致, 避免单段过大导致网络传输失败.
private const val MAX_SEGMENT_BYTES = 6 * 1024 * 1024

// 最小段长度: 16kHz/16bit/mono 下 100ms = 3200 字节。短于这个长度直接丢弃,
// 避免把超短碎片 (比如 stop 时缓冲区里的残留) 发给服务端导致 400。
private const val MIN_SEGMENT_BYTES = 3200

/**
 * 阶跃星辰 Step ASR Controller。
 *
 * 与 MiMo 类似也是 HTTP 一次性提交 + 分段上传, 走 Step ASR SSE 端点:
 * - JSON body: audio.data=<pcm base64> / audio.input.transcription / audio.input.format
 * - 鉴权: `Authorization: Bearer sk-xxx`
 * - 响应: text/event-stream, 事件类型包括 transcript.text.delta / transcript.text.done / error
 *
 * 官方文档: https://platform.stepfun.com/docs/zh/api-reference/audio/asr-sse
 */
class StepASRController(
    private val context: Context,
    private val httpClient: HttpClient,
    private val provider: ASRProviderSetting.Step
) : ASRController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(ASRState(isAvailable = true))
    override val state: StateFlow<ASRState> = _state.asStateFlow()

    private var recorderJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var onTranscriptChange: ((String) -> Unit)? = null

    // 同一时刻只允许一个 flush 协程在跑, 避免乱序拼结果
    private var flushJob: Job? = null

    private val bufferLock = Any()
    private var currentBuffer = ByteArrayOutputStream()
    private var segmentStartElapsedMs = 0L
    private val completedTranscripts = MutableStateFlow<List<String>>(emptyList())

    override fun start(onTranscriptChange: (String) -> Unit) {
        if (state.value.isRecording) return
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            setError("Microphone permission is required")
            return
        }

        this.onTranscriptChange = onTranscriptChange
        synchronized(bufferLock) {
            currentBuffer = ByteArrayOutputStream()
            segmentStartElapsedMs = SystemClock.elapsedRealtime()
        }
        completedTranscripts.value = emptyList()
        flushJob = null

        // Step 是 HTTP 一次性接口, 没有 WebSocket 连接阶段, 直接进入 Listening
        _state.update {
            ASRState(
                status = ASRStatus.Listening,
                isAvailable = true
            )
        }
        startRecorder()
    }

    override fun stop() {
        recorderJob?.cancel()
        releaseRecorder()
        _state.update { it.copy(status = ASRStatus.Stopping) }

        // 把剩余 PCM 做最后一次 flush, 完成后切回 Idle
        scope.launch(Dispatchers.IO) {
            try {
                // 等当前正在跑的 flushJob 完成, 避免并发 flush 导致缓冲区竞争
                flushJob?.join()
                flushSegment()
            } catch (e: Exception) {
                Log.e(TAG, "Final flush failed", e)
                setError(e.message ?: "Step ASR final flush failed")
            } finally {
                _state.update { it.copy(status = ASRStatus.Idle) }
            }
        }
    }

    override fun dispose() {
        recorderJob?.cancel()
        flushJob?.cancel()
        releaseRecorder()
        scope.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun startRecorder() {
        recorderJob?.cancel()
        recorderJob = scope.launch(Dispatchers.IO) {
            val sampleRate = provider.sampleRate
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = minBufferSize
                .coerceAtLeast(sampleRate / 10 * 2)
                .coerceAtLeast(4096)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )
            audioRecord = recorder

            try {
                recorder.startRecording()
                val buffer = ByteArray(bufferSize)
                val segmentMs = provider.segmentDurationSec.coerceAtLeast(0) * 1000L
                while (isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val amplitude = calculateRmsAmplitude(buffer, read)
                        _state.update { it.copy(amplitudes = it.amplitudes.appendAmplitude(amplitude)) }

                        val shouldFlush = synchronized(bufferLock) {
                            currentBuffer.write(buffer, 0, read)
                            if (segmentMs <= 0) {
                                currentBuffer.size() >= MAX_SEGMENT_BYTES
                            } else {
                                val elapsed = SystemClock.elapsedRealtime() - segmentStartElapsedMs
                                currentBuffer.size() >= MAX_SEGMENT_BYTES || elapsed >= segmentMs
                            }
                        }

                        if (shouldFlush) {
                            // 用单独协程异步 flush, 不阻塞录音主循环
                            triggerFlush()
                        }
                    } else if (read < 0) {
                        throw IllegalStateException("AudioRecord read error: $read")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio recording failed", e)
                setError(e.message ?: "Audio recording failed")
            } finally {
                releaseRecorder()
            }
        }
    }

    private fun triggerFlush() {
        // 同一时刻只跑一个 flush, 避免后发先至导致结果乱序
        if (flushJob?.isActive == true) return
        flushJob = scope.launch(Dispatchers.IO) {
            runCatching { flushSegment() }
                .onFailure { Log.e(TAG, "Segment flush failed", it) }
        }
    }

    /**
     * 取出当前缓冲区里的 PCM, base64 后用 JSON 上传到 Step /v1/audio/asr/sse。
     * 响应是 SSE, 把 delta/done 事件拼成识别结果后加到 completedTranscripts。
     *
     * 在 bufferLock 内拷贝出 PCM 并立刻重置缓冲区, 不持有锁等待网络, 避免阻塞录音写。
     */
    private suspend fun flushSegment() {
        val pcmBytes = synchronized(bufferLock) {
            if (currentBuffer.size() == 0) return
            val bytes = currentBuffer.toByteArray()
            currentBuffer = ByteArrayOutputStream()
            segmentStartElapsedMs = SystemClock.elapsedRealtime()
            bytes
        }

        // 太短的段直接丢弃, 避免服务端因音频过短返回 400
        // (16kHz/16bit/mono 下 6400 字节 = 200ms, 短于这个长度服务端通常无法识别)
        if (pcmBytes.size < MIN_SEGMENT_BYTES) {
            Log.d(TAG, "Skip flush: PCM too short (${pcmBytes.size} bytes)")
            return
        }

        val text = transcribeStepAudio(httpClient, provider, pcmBytes).trim()

        if (text.isNotEmpty()) {
            completedTranscripts.update { it + text }
            publishTranscript()
        }
    }

    private fun publishTranscript() {
        val transcript = completedTranscripts.value
            .filter { it.isNotBlank() }
            .joinToString(" ")
        _state.update { it.copy(transcript = transcript, errorMessage = null) }
        scope.launch { onTranscriptChange?.invoke(transcript) }
    }

    private fun setError(message: String) {
        _state.update {
            it.copy(
                status = ASRStatus.Error,
                errorMessage = message
            )
        }
    }

    private fun releaseRecorder() {
        recorderJob = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
    }
}
