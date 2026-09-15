package me.rerere.rikkahub.utils

import me.rerere.asr.ASRStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import me.rerere.common.logging.RikkaLog
import me.rerere.rikkahub.generated.resources.Res
import javax.sound.sampled.AudioSystem

private object AsrSounds {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val clips = scope.async {
        listOf(ASRStatus.Listening to "start", ASRStatus.Stopping to "stop").associate { (status, name) ->
            status to runCatching {
                val bytes = Res.readBytes("files/asr/$name.wav")
                AudioSystem.getAudioInputStream(bytes.inputStream()).use { stream ->
                    AudioSystem.getClip().apply { open(stream) }
                }
            }.onFailure { RikkaLog.w("AsrSounds", "Could not load ASR sound", it) }.getOrNull()
        }
    }
}

actual fun preloadAsrSounds() { AsrSounds.clips.start() }

actual fun playAsrSound(status: ASRStatus) {
    AsrSounds.scope.launch {
        AsrSounds.clips.await()[status]?.let { clip ->
            clip.stop()
            clip.framePosition = 0
            clip.start()
        }
    }
}
