package me.rerere.rikkahub.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import me.rerere.rikkahub.generated.resources.Res
import me.rerere.rikkahub.generated.resources.jetbrains_mono
import org.jetbrains.compose.resources.Font

@Composable
fun jetbrainsMonoFontFamily(): FontFamily = FontFamily(Font(Res.font.jetbrains_mono))
