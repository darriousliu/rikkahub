package me.rerere.rikkahub.di

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.async.prepare
import androidx.sqlite.async.step
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.vinceglb.filekit.PlatformFile
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.HttpHeaders
import korlibs.template.KorteTemplates
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.util.KeyRoulette
import me.rerere.ai.util.lru
import me.rerere.common.http.AcceptLanguageBuilder
import me.rerere.common.logging.RikkaLog as Log
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.AIRequestInterceptor
import me.rerere.rikkahub.data.ai.RequestLoggingInterceptor
import me.rerere.rikkahub.data.ai.mcp.AndroidMcpImageStore
import me.rerere.rikkahub.data.ai.mcp.McpImageStore
import me.rerere.rikkahub.data.datastore.ANDROID_DEFAULT_PROVIDER_DESCRIPTIONS
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.createAndroidSettingsDataStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseConstructor
import me.rerere.rikkahub.data.db.buildAppDatabase
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.db.fts.SimpleDictManager
import me.rerere.rikkahub.data.sync.BackupFileLayout
import me.rerere.rikkahub.shared.PlatformBuildInfo
import me.rerere.rikkahub.shared.apiUserAgent
import me.rerere.rikkahub.shared.applyAppTimeouts
import me.rerere.rikkahub.shared.template.createMessageTemplateEngine
import me.rerere.search.SearchService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.dsl.module

val androidDataSourceModule = module {
    includes(dataSourceModule)
    single {
        SettingsStore(
            dataStore = createAndroidSettingsDataStore(context = get(), scope = get<AppScope>()),
            scope = get<AppScope>(),
            defaultProviderDescriptions = ANDROID_DEFAULT_PROVIDER_DESCRIPTIONS,
            onSettingsChanged = { get<KorteTemplates>().invalidateCache() },
        )
    }

    single {
        val context: Context = get()
        createAndroidAppDatabase(context)
    }

    single { createMessageTemplateEngine() }
    single { MessageFtsManager(get(), MessageFtsDialect.SIMPLE) }

    single<McpImageStore> { AndroidMcpImageStore(get()) }

    single<OkHttpClient> {
        val acceptLang = AcceptLanguageBuilder.fromAndroid(get())
            .build()
        val buildInfo = get<PlatformBuildInfo>()
        OkHttpClient.Builder()
            .applyAppTimeouts()
            .followSslRedirects(true)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestBuilder = originalRequest.newBuilder()
                    .addHeader(HttpHeaders.AcceptLanguage, acceptLang)

                if (originalRequest.header(HttpHeaders.UserAgent) == null) {
                    requestBuilder.addHeader(HttpHeaders.UserAgent, buildInfo.apiUserAgent("Android"))
                }

                chain.proceed(requestBuilder.build())
            }
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val contentTypeHeader = request.header("Content-Type")
                if (
                    contentTypeHeader != null &&
                    contentTypeHeader.contains(";") &&
                    contentTypeHeader.substringBefore(";").trim().equals("application/json", ignoreCase = true)
                ) {
                    chain.proceed(
                        request.newBuilder()
                            .header("Content-Type", contentTypeHeader.substringBefore(";").trim())
                            .build()
                    )
                } else {
                    chain.proceed(request)
                }
            }
            .addNetworkInterceptor(RequestLoggingInterceptor())
            .addInterceptor(AIRequestInterceptor())
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            })
            .build().also { okHttpClient ->
                SearchService.init(
                    client = HttpClient(OkHttp) {
                        engine { preconfigured = okHttpClient }
                    },
                    keyRoulette = KeyRoulette.lru(get()),
                )
            }
    }

    single {
        val aiOkHttpClient = get<OkHttpClient>()
        ProviderManager(
            client = HttpClient(OkHttp) {
                engine {
                    preconfigured = aiOkHttpClient
                }
            },
            keyRoulette = KeyRoulette.lru(get()),
        )
    }

    single {
        val context: Context = get()
        BackupFileLayout(
            filesRoot = PlatformFile(context.filesDir),
            cacheRoot = PlatformFile(context.cacheDir),
            databaseFiles = mapOf(
                "rikka_hub.db" to PlatformFile(context.getDatabasePath("rikka_hub")),
                "rikka_hub-wal" to PlatformFile(context.getDatabasePath("rikka_hub-wal")),
                "rikka_hub-shm" to PlatformFile(context.getDatabasePath("rikka_hub-shm")),
            ),
        )
    }

    single<HttpClient> {
        HttpClient(OkHttp) {
            install(WebSockets) {
                channels {
                    outgoing = bounded(capacity = 1)
                }
            }
            engine {
                config {
                    applyAppTimeouts()
                    followSslRedirects(true)
                    followRedirects(true)
                    retryOnConnectionFailure(true)
                }
            }
        }
    }

}

internal fun createAndroidAppDatabase(
    context: Context,
    name: String = "rikka_hub",
): AppDatabase {
    val driver = BundledSQLiteDriver().apply {
        addExtension(context.applicationInfo.nativeLibraryDir + "/libsimple.so")
    }
    return buildAppDatabase(
        builder = Room.databaseBuilder<AppDatabase>(
            context = context,
            name = name,
            factory = AppDatabaseConstructor::initialize,
        ),
        driver = driver,
        ftsDialect = MessageFtsDialect.SIMPLE,
        platformOnOpen = { connection ->
            val dictDir = SimpleDictManager.extractDict(context)
            connection.prepare("SELECT jieba_dict(?)").use { statement ->
                statement.bindText(1, dictDir.absolutePath)
                if (statement.step()) {
                    val result = statement.getText(0)
                    val success = result.trimEnd('/') == dictDir.absolutePath.trimEnd('/')
                    if (!success) {
                        Log.e(
                            "DataSourceModule",
                            "jieba_dict failed: $result, path=${dictDir.absolutePath}"
                        )
                    }
                }
            }
        },
    )
}
