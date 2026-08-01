package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.rerere.rikkahub.generated.resources.Res
import me.rerere.rikkahub.generated.resources.siliconflow_dark
import me.rerere.rikkahub.generated.resources.siliconflow_light
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import org.jetbrains.compose.resources.painterResource

@Composable
fun SiliconFlowPowerByIcon(modifier: Modifier = Modifier) {
    val darkMode = LocalDarkMode.current
    Image(
        painter = painterResource(
            if (darkMode) Res.drawable.siliconflow_dark else Res.drawable.siliconflow_light,
        ),
        contentDescription = null,
        modifier = modifier,
    )
}
