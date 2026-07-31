package me.rerere.rikkahub.ui.components.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import java.util.Calendar
import me.rerere.rikkahub.generated.resources.*
import me.rerere.rikkahub.ui.resources.stringResource

@Composable
fun Greeting(
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium
) {
    @Composable
    fun getGreetingMessage(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> stringResource(id = Res.string.menu_page_morning_greeting)
            in 12..17 -> stringResource(id = Res.string.menu_page_afternoon_greeting)
            in 18..22 -> stringResource(id = Res.string.menu_page_evening_greeting)
            else -> stringResource(id = Res.string.menu_page_night_greeting)
        }
    }

    Text(
        text = getGreetingMessage(),
        style = style,
        modifier = modifier
    )
}
