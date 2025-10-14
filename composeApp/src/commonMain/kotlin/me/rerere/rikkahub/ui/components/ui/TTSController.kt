package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import com.composables.icons.lucide.*
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.hooks.CustomTtsState
import me.rerere.tts.model.PlaybackState
import me.rerere.tts.model.PlaybackStatus
import net.sergeych.sprintf.format

@Composable
fun TTSController() {
    val context = LocalPlatformContext.current
    val ttsState = LocalTTSState.current

    val isSpeaking by ttsState.isSpeaking.collectAsState()
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(isSpeaking) {
        if (isSpeaking) {
            // 如果开启，显示悬浮窗
            isVisible = true
        }
    }

    FloatingWindow(
        tag = "tts_controller",
        visibility = isVisible
    ) {
        val playbackState by ttsState.playbackState.collectAsState()
        var expand by remember { mutableStateOf(false) }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.padding(8.dp),
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayPauseButton(playbackState = playbackState, ttsState = ttsState)

                IconButton(
                    onClick = {
                        ttsState.stop()
                        isVisible = false
                    }
                ) {
                    Icon(
                        imageVector = Lucide.X,
                        contentDescription = null,
                    )
                }

                AnimatedVisibility(expand) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SpeedButton(playbackState, ttsState)

                        FastForwardButton(ttsState = ttsState)
                    }
                }

                IconButton(
                    onClick = {
                        expand = !expand
                    }
                ) {
                    Icon(
                        imageVector = if (expand) Lucide.ChevronLeft else Lucide.ChevronRight,
                        contentDescription = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun FastForwardButton(ttsState: CustomTtsState) {
    IconButton(
        onClick = {
            ttsState.fastForward(5000)
        }
    ) {
        Icon(
            imageVector = Lucide.FastForward,
            contentDescription = null,
        )
    }
}

@Composable
private fun PlayPauseButton(
    playbackState: PlaybackState,
    ttsState: CustomTtsState
) {
    FilledTonalIconButton(
        onClick = {
            when (playbackState.status) {
                PlaybackStatus.Playing -> {
                    ttsState.pause()
                }

                else -> {
                    ttsState.resume()
                }
            }
        },
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Icon(
            imageVector = if (playbackState.status == PlaybackStatus.Playing) Lucide.Pause else Lucide.Play,
            contentDescription = null,
        )
        if (playbackState.status == PlaybackStatus.Playing || playbackState.status == PlaybackStatus.Buffering || playbackState.status == PlaybackStatus.Paused) {
            CircularProgressIndicator(
                progress = {
                    if (playbackState.status == PlaybackStatus.Playing) {
                        playbackState.positionMs.toFloat() / playbackState.durationMs
                    } else {
                        0f
                    }
                },
                color = MaterialTheme.colorScheme.tertiary,
                strokeWidth = 2.dp,
                trackColor = Color.Transparent
            )
            CircularProgressIndicator(
                progress = {
                    if (playbackState.status == PlaybackStatus.Playing) {
                        playbackState.currentChunkIndex.toFloat() / playbackState.totalChunks
                    } else {
                        0f
                    }
                },
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(2.dp),
                strokeWidth = 2.dp,
                trackColor = Color.Transparent
            )
        }
    }
}

@Composable
private fun SpeedButton(
    playbackState: PlaybackState,
    ttsState: CustomTtsState
) {
    TextButton(
        onClick = {
            when (playbackState.speed) {
                0.8f -> {
                    ttsState.setSpeed(1.0f)
                }

                1.0f -> {
                    ttsState.setSpeed(1.2f)
                }

                1.2f -> {
                    ttsState.setSpeed(1.5f)
                }

                1.5f -> {
                    ttsState.setSpeed(0.8f)
                }

                else -> {
                    ttsState.setSpeed(1.0f)
                }
            }
        }
    ) {
        Text(text = "x${"%.1f".format(playbackState.speed)}")
    }
}
