package me.rerere.rikkahub.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.rerere.asr.IosMicrophone
import me.rerere.asr.Microphone

@Composable
internal actual fun rememberMicrophone(): Microphone = remember { IosMicrophone() }
