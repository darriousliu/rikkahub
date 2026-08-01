package me.rerere.rikkahub.ui.components.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
expect fun AutoAIIcon(
    name: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
)
