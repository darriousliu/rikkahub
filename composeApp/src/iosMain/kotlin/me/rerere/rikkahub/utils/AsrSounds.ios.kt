package me.rerere.rikkahub.utils

import me.rerere.asr.ASRStatus
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import me.rerere.common.logging.RikkaLog
import me.rerere.rikkahub.generated.resources.Res
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSData
import platform.Foundation.create

@OptIn(ExperimentalForeignApi::class)
private object AsrSounds {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val players = scope.async {
        listOf(ASRStatus.Listening to "start", ASRStatus.Stopping to "stop").associate { (status, name) ->
            status to runCatching {
                val data = Res.readBytes("files/asr/$name.wav").usePinned {
                    NSData.create(bytes = it.addressOf(0), length = it.get().size.toULong())
                }
                AVAudioPlayer(data, error = null).apply { prepareToPlay() }
            }.onFailure { RikkaLog.w("AsrSounds", "Could not load ASR sound", it) }.getOrNull()
        }
    }
}

actual fun preloadAsrSounds() { AsrSounds.players.start() }

actual fun playAsrSound(status: ASRStatus) {
    AsrSounds.scope.launch {
        AsrSounds.players.await()[status]?.let { player ->
            player.currentTime = 0.0
            player.play()
        }
    }
}
