package me.rerere.rikkahub.ui.components.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import rikkahub.composeapp.generated.resources.*
import kotlin.time.Clock

@Composable
fun Greeting(
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium
) {
    @Composable
    fun getGreetingMessage(): String {
        val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
        return when (hour) {
            in 5..11 -> stringResource(Res.string.menu_page_morning_greeting)
            in 12..17 -> stringResource(Res.string.menu_page_afternoon_greeting)
            in 18..22 -> stringResource(Res.string.menu_page_evening_greeting)
            else -> stringResource(Res.string.menu_page_night_greeting)
        }
    }

    Text(
        text = getGreetingMessage(),
        style = style,
        modifier = modifier
    )
}
