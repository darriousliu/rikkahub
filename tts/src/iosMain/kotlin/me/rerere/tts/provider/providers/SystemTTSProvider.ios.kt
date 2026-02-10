package me.rerere.tts.provider.providers

import co.touchlab.kermit.Logger
import kotlinx.cinterop.get
import kotlinx.cinterop.pointed
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import me.rerere.common.PlatformContext
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.AVSpeechUtteranceDefaultSpeechRate
import platform.AVFAudio.AVSpeechUtteranceMaximumSpeechRate
import platform.AVFAudio.AVSpeechUtteranceMinimumSpeechRate
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "SystemTTSProvider"

/**
 * 用普通 Kotlin data class 代替 C 结构体 AudioStreamBasicDescription，
 * 避免 memScoped 悬空指针问题
 */
private data class AudioDesc(
    val sampleRate: Double,
    val channelsPerFrame: UInt,
    val bitsPerChannel: UInt,
//    val bytesPerFrame: UInt,
//    val framesPerPacket: UInt,
//    val bytesPerPacket: UInt,
//    val formatID: UInt,
//    val formatFlags: UInt,
)

actual class SystemTTSProvider actual constructor() :
    TTSProvider<TTSProviderSetting.SystemTTS> {
    actual override fun generateSpeech(
        context: PlatformContext,
        providerSetting: TTSProviderSetting.SystemTTS,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val audioData = synthesize(providerSetting, request)
        emit(
            AudioChunk(
                data = audioData,
                format = AudioFormat.WAV,
                isLast = true,
                metadata = mapOf(
                    "provider" to "system",
                    "speechRate" to providerSetting.speechRate.toString(),
                    "pitch" to providerSetting.pitch.toString()
                )
            )
        )
    }

    private suspend fun synthesize(
        providerSetting: TTSProviderSetting.SystemTTS,
        request: TTSRequest
    ): ByteArray = suspendCancellableCoroutine { continuation ->
        val utterance = AVSpeechUtterance(string = request.text).apply {
            // iOS speechRate 范围: AVSpeechUtteranceMinimumSpeechRate(0.0) ~ AVSpeechUtteranceMaximumSpeechRate(1.0)
            // 默认 AVSpeechUtteranceDefaultSpeechRate ≈ 0.5
            // Android 默认 1.0 = 正常速度，映射到 iOS 的 0.5
            rate = (providerSetting.speechRate * AVSpeechUtteranceDefaultSpeechRate)
                .coerceIn(AVSpeechUtteranceMinimumSpeechRate, AVSpeechUtteranceMaximumSpeechRate)
            pitchMultiplier = providerSetting.pitch
                .coerceIn(0.5f, 2.0f)
            // 使用系统默认语音
            voice = AVSpeechSynthesisVoice.voiceWithLanguage(null)
        }
        val synthesizer = AVSpeechSynthesizer()
        val buffers = mutableListOf<ByteArray>()
        var audioDescription: AudioDesc? = null
        // AVSpeechSynthesizer.write(_:toBufferCallback:) — iOS 13+
        // 回调在内部队列，多次调用，最后一次 buffer 为空时表示完成
        synthesizer.writeUtterance(utterance) { buffer ->
            val pcmBuffer = buffer as? AVAudioPCMBuffer
            if (pcmBuffer == null || pcmBuffer.frameLength == 0u) {
                // 合成完成
                Logger.i(TAG) { "synthesize: TTS synthesis completed, ${buffers.size} chunks" }
                try {
                    if (buffers.isEmpty()) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                Exception("TTS synthesis produced no audio data")
                            )
                        }
                        return@writeUtterance
                    }
                    val desc = audioDescription
                    if (desc == null) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                Exception("No audio format description available")
                            )
                        }
                        return@writeUtterance
                    }
                    // 拼接所有 PCM 数据
                    val totalSize = buffers.sumOf { it.size }
                    val pcmData = ByteArray(totalSize)
                    var offset = 0
                    for (chunk in buffers) {
                        chunk.copyInto(pcmData, offset)
                        offset += chunk.size
                    }
                    // 包装为 WAV
                    val wavData = createWavFile(
                        pcmData = pcmData,
                        sampleRate = desc.sampleRate.toInt(),
                        channels = desc.channelsPerFrame.toInt(),
                        bitsPerSample = desc.bitsPerChannel.toInt()
                    )
                    if (continuation.isActive) {
                        continuation.resume(wavData)
                    }
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
                return@writeUtterance
            }
            // ===== 处理音频 buffer =====
            val format = pcmBuffer.format
            val frameLength = pcmBuffer.frameLength.toInt()
            // ✅ 首次回调时，安全地读取格式信息到 Kotlin 对象
            // 记录格式信息（只需要一次）
            if (audioDescription == null) {
                val streamDesc = format.streamDescription
                if (streamDesc != null) {
                    val desc = streamDesc.pointed
                    val sr = desc.mSampleRate
                    val ch = desc.mChannelsPerFrame
                    val bits = desc.mBitsPerChannel
                    Logger.i(TAG) { "Audio format: ${sr}Hz, ${ch}ch, ${bits}bit" }
                    audioDescription = AudioDesc(
                        sampleRate = sr,
                        channelsPerFrame = ch,
                        bitsPerChannel = bits,
                    )
                } else {
                    // streamDescription 为 null 时，用 AVAudioFormat 的属性作为 fallback
                    val sr = format.sampleRate
                    val ch = format.channelCount
                    Logger.w(TAG) { "streamDescription null, fallback: ${sr}Hz, ${ch}ch" }
                    audioDescription = AudioDesc(
                        sampleRate = sr,
                        channelsPerFrame = ch,
                        bitsPerChannel = 32u, // AVSpeechSynthesizer 默认输出 Float32

                    )
                }
            }
            // 提取 PCM 数据
            // ===== 提取 PCM 数据 =====
            val floatChannelData = pcmBuffer.floatChannelData
            if (floatChannelData != null) {
                // Float32 -> Int16 转换
                val channels = audioDescription!!.channelsPerFrame.coerceAtLeast(1u).toInt()
                val int16Data = ByteArray(frameLength * channels * 2)
                for (frame in 0 until frameLength) {
                    for (ch in 0 until channels) {
                        val channelPtr = floatChannelData[ch] ?: continue
                        val sample = channelPtr[frame]
                        val clamped = sample.coerceIn(-1.0f, 1.0f)
                        val int16 = (clamped * 32767f).toInt().toShort()
                        val byteIndex = (frame * channels + ch) * 2
                        int16Data[byteIndex] = (int16.toInt() and 0xFF).toByte()
                        int16Data[byteIndex + 1] = ((int16.toInt() shr 8) and 0xFF).toByte()
                    }
                }
                buffers.add(int16Data)
                // 更新为实际输出的 16-bit
                audioDescription = audioDescription!!.copy(bitsPerChannel = 16u)
            } else {
                val int16Data = pcmBuffer.int16ChannelData
                if (int16Data != null) {
                    val channels = audioDescription.channelsPerFrame.coerceAtLeast(1u).toInt()
                    val bytes = ByteArray(frameLength * channels * 2)
                    for (frame in 0 until frameLength) {
                        for (ch in 0 until channels) {
                            val channelPtr = int16Data[ch] ?: continue
                            val sample = channelPtr[frame]
                            val byteIndex = (frame * channels + ch) * 2
                            bytes[byteIndex] = (sample.toInt() and 0xFF).toByte()
                            bytes[byteIndex + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                        }
                    }
                    buffers.add(bytes)
                } else {
                    // 最后的 fallback：直接读 audioBufferList
                    val audioBufferList = pcmBuffer.audioBufferList
                    if (audioBufferList != null) {
                        val mBuffers = audioBufferList.pointed.mBuffers.pointed
                        val rawData = mBuffers.mData
                        val rawSize = mBuffers.mDataByteSize.toInt()
                        if (rawData != null && rawSize > 0) {
                            buffers.add(rawData.readBytes(rawSize))
                        }
                    }
                }
            }
        }
        continuation.invokeOnCancellation {
            synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        }
    }

    /**
     * 将原始 PCM 数据包装为 WAV 格式
     */
    private fun createWavFile(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val actualBits = if (bitsPerSample == 32) 16 else bitsPerSample
        val byteRate = sampleRate * channels * actualBits / 8
        val blockAlign = channels * actualBits / 8
        val dataSize = pcmData.size
        val header = ByteArray(44)
        // RIFF
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        writeInt32LE(header, 4, 36 + dataSize)
        // WAVE
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        // fmt
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        writeInt32LE(header, 16, 16)
        writeInt16LE(header, 20, 1) // PCM
        writeInt16LE(header, 22, channels)
        writeInt32LE(header, 24, sampleRate)
        writeInt32LE(header, 28, byteRate)
        writeInt16LE(header, 32, blockAlign)
        writeInt16LE(header, 34, actualBits)
        // data
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        writeInt32LE(header, 40, dataSize)
        return header + pcmData
    }

    private fun writeInt32LE(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeInt16LE(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }
}
