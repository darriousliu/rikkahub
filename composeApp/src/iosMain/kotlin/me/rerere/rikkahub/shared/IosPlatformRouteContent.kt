package me.rerere.rikkahub.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.webview.SharedWebViewPage

object IosPlatformRouteContent : PlatformRouteContent {
    @Composable
    override fun Render(screen: Screen) {
        when (screen) {
            is Screen.WebView -> SharedWebViewPage(screen)
            else -> UnavailableRoute(screen)
        }
    }
}

@Composable
private fun UnavailableRoute(screen: Screen) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BackButton()
        Text(
            text = screen::class.simpleName ?: "Unavailable route",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "This feature is not available on iOS yet.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
