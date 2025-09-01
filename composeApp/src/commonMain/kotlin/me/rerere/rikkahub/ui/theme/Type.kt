package me.rerere.rikkahub.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import rikkahub.composeapp.generated.resources.Res
import rikkahub.composeapp.generated.resources.jetbrains_mono

// Set of Material typography styles to start with
val Typography = Typography()

@OptIn(ExperimentalTextApi::class)
val JetbrainsMono: FontFamily
    @Composable
    get() = FontFamily(
        Font(
            resource = Res.font.jetbrains_mono,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(FontWeight.Normal.weight),
            )
        )
    )
