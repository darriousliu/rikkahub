package me.rerere.rikkahub.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import me.rerere.asr.AndroidMicrophone
import me.rerere.asr.Microphone

@Composable
internal actual fun rememberMicrophone(): Microphone {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidMicrophone(context) }
}
