package me.rerere.rikkahub.utils

import androidx.compose.runtime.Composable
import coil3.Image
import org.jetbrains.compose.resources.DrawableResource

@Composable
expect fun DrawableResource.toCoilImage(): Image
