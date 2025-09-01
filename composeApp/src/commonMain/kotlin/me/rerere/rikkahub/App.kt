package me.rerere.rikkahub

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import org.koin.compose.getKoin

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

class AppScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default)

