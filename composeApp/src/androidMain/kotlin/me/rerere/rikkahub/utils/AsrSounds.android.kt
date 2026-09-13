package me.rerere.rikkahub.utils

import me.rerere.asr.ASRStatus
import me.rerere.rikkahub.shared.R
import org.koin.mp.KoinPlatform

actual fun preloadAsrSounds() {
    KoinPlatform.getKoin().get<SoundEffectPlayer>().preload(R.raw.asr_start, R.raw.asr_stop)
}

actual fun playAsrSound(status: ASRStatus) {
    val player = KoinPlatform.getKoin().get<SoundEffectPlayer>()
    when (status) {
        ASRStatus.Listening -> player.play(R.raw.asr_start)
        ASRStatus.Stopping -> player.play(R.raw.asr_stop)
        else -> Unit
    }
}
