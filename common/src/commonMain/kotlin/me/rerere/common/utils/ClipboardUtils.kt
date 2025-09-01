package me.rerere.common.utils

import androidx.compose.ui.platform.ClipEntry

expect fun provideClipEntry(label:String, text: String): ClipEntry
