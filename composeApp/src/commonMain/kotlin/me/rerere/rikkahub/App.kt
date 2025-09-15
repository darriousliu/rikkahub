package me.rerere.rikkahub

import androidx.compose.runtime.Composable
import co.touchlab.kermit.Logger
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import org.koin.compose.getKoin

private const val TAG = "RikkaHubApp"

const val CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID = "chat_completed"

@Composable
fun App(
    platformConfigure: @Composable (darkTheme: Boolean) -> Unit = {},
    content: @Composable () -> Unit
) {
    val koin = getKoin()
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .crossfade(true)
            .components {
                add(KtorNetworkFetcherFactory(koin.get<HttpClient>()))
                add(SvgDecoder.Factory(scaleToDensity = true))
            }
            .build()
    }
    RikkahubTheme(
        platformConfigure = platformConfigure,
    ) {
        content()
    }
}

class AppScope : CoroutineScope by CoroutineScope(
    SupervisorJob()
        + Dispatchers.Main
        + CoroutineName("AppScope")
        + CoroutineExceptionHandler { _, e ->
        Logger.e(TAG, e) { "AppScope exception" }
    }
)

