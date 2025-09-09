package me.rerere.rikkahub.di

import androidx.room.Room
import de.jensklingenberg.ktorfit.Ktorfit
import dev.gitlive.firebase.remoteconfig.FirebaseRemoteConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.sse.SSE
import io.ktor.serialization.kotlinx.json.json
import io.pebbletemplates.pebble.PebbleEngine
import me.rerere.ai.provider.ProviderManager
import me.rerere.rikkahub.data.ai.AIRequestInterceptorPlugin
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.transformers.AssistantTemplateLoader
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.api.SponsorAPI
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.Migration_6_7
import me.rerere.rikkahub.data.mcp.McpManager
import me.rerere.rikkahub.data.sync.DataSync
import org.koin.dsl.module
import java.util.Locale

val dataSourceModule = module {
    single {
        SettingsStore(context = get(), scope = get())
    }

    single {
        Room.databaseBuilder(get(), AppDatabase::class.java, "rikka_hub")
            .addMigrations(Migration_6_7)
            .build()
    }

    single {
        AssistantTemplateLoader(settingsStore = get())
    }

    single {
        PebbleEngine.Builder()
            .loader(get<AssistantTemplateLoader>())
            .defaultLocale(Locale.getDefault())
            .autoEscaping(false)
            .build()
    }

    single { TemplateTransformer(engine = get(), settingsStore = get()) }

    single {
        get<AppDatabase>().conversationDao()
    }

    single {
        get<AppDatabase>().memoryDao()
    }

    single {
        get<AppDatabase>().genMediaDao()
    }

    single { McpManager(settingsStore = get(), appScope = get()) }

    single {
        GenerationHandler(
            context = get(),
            providerManager = get(),
            json = get(),
            memoryRepo = get(),
            conversationRepo = get()
        )
    }

    single<HttpClient> {
        HttpClient {
            install(ContentNegotiation) {
                json(json = get())
            }
            install(SSE)
            install(HttpRedirect) {
                checkHttpMethod = true
                allowHttpsDowngrade = true
            }
            install(HttpRequestRetry) {
                maxRetries = 3
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 10 * 60 * 1000
                requestTimeoutMillis = 20_000 + 10 * 60 * 1000 + 120_000
            }
            install(Logging) {
                level = LogLevel.HEADERS
            }
            install(AIRequestInterceptorPlugin) {
                remoteConfig = get<FirebaseRemoteConfig>()
            }
        }
    }

    single {
        SponsorAPI.create(get())
    }

    single {
        ProviderManager(client = get())
    }

    single {
        DataSync(settingsStore = get(), json = get(), context = get())
    }

    single<Ktorfit> {
        Ktorfit.Builder()
            .baseUrl("https://api.rikka-ai.com")
            .httpClient(client = get())
            .build()
    }

//    single<RikkaHubAPI> {
//        get<Ktorfit>().create()
//    }
}
