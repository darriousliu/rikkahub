package me.rerere.asr.providers

import io.ktor.client.HttpClient
import me.rerere.common.logging.RikkaLog as Log
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.io.Buffer
import kotlinx.io.writeIntLe
import kotlinx.io.writeShortLe
import kotlinx.io.readByteArray
import kotlin.time.TimeSource
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

private const val TAG = "MiMoASR"

// MiMo 官方限制: 单次请求 base64 不超过 10MB ≈ 7.5MB raw。
// 16kHz / 16bit / mono 下 7.5MB ≈ 234 秒。提前在 6MB 触发自动 flush 留余量。
private const val MAX_SEGMENT_BYTES = 6 * 1024 * 1024

/**
 * 小米 MiMo ASR Controller。
 *
 * MiMo ASR 不是 WebSocket 流式接口, 而是 OpenAI 兼容的 chat/completions HTTP 一次性
 * 识别接口。本 Controller 在录音期间按时间或字节阈值把 PCM 切成段, 每段独立转 WAV
 * 后 POST 到 MiMo, 返回的文本拼接到 completedTranscripts 并通过 onTranscriptChange
 * 回调。stop() 时把剩余 PCM 做最后一次 flush。
 *
 * 官方文档: https://platform.xiaomimimo.com/docs/zh-CN/api/audio/Speech-Recognition
 */
class MiMoASRController(
    private val microphone: Microphone,
    private val httpClient: HttpClient,
    private val provider: ASRProviderSetting.MiMo
) : ASRController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(ASRState(isAvailable = true))
    override val state: StateFlow<ASRState> = _state.asStateFlow()

    private var recorderJob: Job? = null
    private var audioRecord: PcmRecorder? = null
    private var onTranscriptChange: ((String) -> Unit)? = null

    // 同一时刻只允许一个 flush 协程在跑, 避免乱序拼结果
    private var flushJob: Job? = null

    private val bufferLock = SynchronizedObject()
    private var currentBuffer = Buffer()
    private var segmentStart = TimeSource.Monotonic.markNow()
    private val completedTranscripts = MutableStateFlow<List<String>>(emptyList())

    override fun start(onTranscriptChange: (String) -> Unit) {
        if (state.value.isRecording) return
        if (!microphone.hasPermission) {
            setError("Microphone permission is required")
            return
        }

        this.onTranscriptChange = onTranscriptChange
        synchronized(bufferLock) {
            currentBuffer = Buffer()
            segmentStart = TimeSource.Monotonic.markNow()
        }
        completedTranscripts.value = emptyList()
        flushJob = null

        // MiMo 是 HTTP 一次性接口, 没有 WebSocket 连接阶段, 直接进入 Listening
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
                // 等当前正在跑的 flushJob 完成, 避免并发 flush 导致结果乱序
                flushJob?.join()
                flushSegment()
            } catch (e: Exception) {
                Log.e(TAG, "Final flush failed", e)
                setError(e.message ?: "MiMo ASR final flush failed")
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

    private fun startRecorder() {
        recorderJob?.cancel()
        recorderJob = scope.launch(Dispatchers.IO) {
            val sampleRate = provider.sampleRate
            val recorder = microphone.createRecorder(sampleRate, (sampleRate / 10 * 2).coerceAtLeast(4096))
            val bufferSize = recorder.bufferSize
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
                                currentBuffer.size >= MAX_SEGMENT_BYTES
                            } else {
                                val elapsed = segmentStart.elapsedNow().inWholeMilliseconds
                                currentBuffer.size >= MAX_SEGMENT_BYTES || elapsed >= segmentMs
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

    private fun triggerFlush() {
        // 同一时刻只跑一个 flush, 避免后发先至导致结果乱序
        if (flushJob?.isActive == true) return
        flushJob = scope.launch(Dispatchers.IO) {
            runCatching { flushSegment() }
                .onFailure { Log.e(TAG, "Segment flush failed", it) }
        }
    }

    /**
     * 取出当前缓冲区里的 PCM, 转 WAV 后 POST 到 MiMo; 把识别结果加到 completedTranscripts。
     * 在 bufferLock 内拷贝出 PCM 并立刻重置缓冲区, 不持有锁等待网络, 避免阻塞录音写。
     */
    private suspend fun flushSegment() {
        val pcmBytes = synchronized(bufferLock) {
            if (currentBuffer.size == 0L) return
            val bytes = currentBuffer.readByteArray()
            currentBuffer = Buffer()
            segmentStart = TimeSource.Monotonic.markNow()
            bytes
        }

        val wavBytes = pcm16ToWav(
            pcm = pcmBytes,
            sampleRate = provider.sampleRate,
            channels = 1,
            bitsPerSample = 16
        )
        val text = transcribeMiMoAudio(httpClient, provider, wavBytes)

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

    companion object {
        /**
         * 把 raw PCM16 little-endian 数据封装成最小 WAV (RIFF/WAVE/fmt/data)。
         * MiMo 官方只接受 WAV/MP3, AudioRecord 输出的是 PCM, 必须自己包 WAV 头。
         */
        private fun pcm16ToWav(
            pcm: ByteArray,
            sampleRate: Int,
            channels: Int,
            bitsPerSample: Int
        ): ByteArray {
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = channels * bitsPerSample / 8
            val dataSize = pcm.size
            val out = Buffer()

            // RIFF header
            out.write("RIFF".encodeToByteArray())
            out.writeIntLe(36 + dataSize) // chunk size = file size - 8
            out.write("WAVE".encodeToByteArray())
            // fmt chunk
            out.write("fmt ".encodeToByteArray())
            out.writeIntLe(16)            // PCM fmt chunk size
            out.writeShortLe(1.toShort())           // audio format = PCM
            out.writeShortLe(channels.toShort())
            out.writeIntLe(sampleRate)
            out.writeIntLe(byteRate)
            out.writeShortLe(blockAlign.toShort())
            out.writeShortLe(bitsPerSample.toShort())
            // data chunk
            out.write("data".encodeToByteArray())
            out.writeIntLe(dataSize)
            out.write(pcm)
            return out.readByteArray()
        }

    }
}
