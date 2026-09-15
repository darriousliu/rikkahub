package me.rerere.rikkahub.di

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.crashlytics
import io.github.vinceglb.filekit.PlatformFile
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.HttpHeaders
import java.io.File
import korlibs.template.KorteTemplates
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.util.KeyRoulette
import me.rerere.ai.util.lru
import me.rerere.common.http.AcceptLanguageBuilder
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.AIRequestInterceptor
import me.rerere.rikkahub.data.ai.RequestLoggingInterceptor
import me.rerere.rikkahub.data.ai.tools.local.AndroidLocalTools
import me.rerere.rikkahub.data.ai.tools.local.LocalTools
import me.rerere.rikkahub.data.ai.transformers.AndroidDocumentTextExtractor
import me.rerere.rikkahub.data.ai.transformers.DocumentTextExtractor
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.WorkspaceReminderTransformer
import me.rerere.rikkahub.data.datastore.ANDROID_DEFAULT_PROVIDER_DESCRIPTIONS
import me.rerere.rikkahub.data.datastore.AndroidBooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.AndroidStringPreferenceStore
import me.rerere.rikkahub.data.datastore.BooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.StringPreferenceStore
import me.rerere.rikkahub.data.datastore.createAndroidSettingsDataStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseConstructor
import me.rerere.rikkahub.data.db.buildAppDatabase
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.sync.BackupFileLayout
import me.rerere.rikkahub.platform.AnalyticsTracker
import me.rerere.rikkahub.platform.AndroidExternalUriOpener
import me.rerere.rikkahub.platform.AndroidFirebaseAnalyticsTracker
import me.rerere.rikkahub.platform.AndroidFirebaseCrashReporter
import me.rerere.rikkahub.platform.AndroidJmDnsServiceRegistrar
import me.rerere.rikkahub.platform.AndroidOAuthCallbackSessionFactory
import me.rerere.rikkahub.platform.ChatNotificationPresenter
import me.rerere.rikkahub.platform.CrashReporter
import me.rerere.rikkahub.platform.ExternalUriOpener
import me.rerere.rikkahub.platform.OAuthCallbackSessionFactory
import me.rerere.rikkahub.service.AndroidChatNotificationPresenter
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.createWorkspaceToolsIfReady
import me.rerere.rikkahub.shared.PlatformBuildInfo
import me.rerere.rikkahub.shared.apiUserAgent
import me.rerere.rikkahub.shared.applyAppTimeouts
import me.rerere.rikkahub.shared.createPlatformBuildInfo
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceDetailVM
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceVM
import me.rerere.rikkahub.utils.SoundEffectPlayer
import me.rerere.rikkahub.web.AndroidWebServerRuntime
import me.rerere.rikkahub.web.KtorWebServerHost
import me.rerere.rikkahub.web.WebServerManager
import me.rerere.rikkahub.web.WebServerRuntime
import me.rerere.rikkahub.web.configureWebApi
import me.rerere.search.SearchService
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.provider.providers.SystemTTSProvider
import me.rerere.workspace.ProotShellRunner
import me.rerere.workspace.RootfsInstaller
import me.rerere.workspace.WorkspaceBindMount
import me.rerere.workspace.WorkspaceManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val androidModule = module {
    single<ChatNotificationPresenter> { AndroidChatNotificationPresenter(get()) }
    single<TTSProvider<TTSProviderSetting.SystemTTS>> { SystemTTSProvider(get()) }

    single<InputMessageTransformer>(named("workspaceReminder")) {
        WorkspaceReminderTransformer(get())
    }
    single<suspend (String?, String?) -> List<Tool>>(named("workspaceTools")) {
        val repository = get<WorkspaceRepository>()
        val createTools: suspend (String?, String?) -> List<Tool> = { id, cwd ->
            createWorkspaceToolsIfReady(repository, id, cwd)
        }
        createTools
    }
    single<PlatformBuildInfo> {
        val context = get<Context>()
        createPlatformBuildInfo(
            debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            applicationId = context.packageName,
            systemDescription = buildString {
                append(Build.MANUFACTURER)
                append(' ')
                append(Build.MODEL)
                append(" / Android ")
                append(Build.VERSION.RELEASE)
                append(" / SDK ")
                append(Build.VERSION.SDK_INT)
            },
        )
    }

    single<BooleanPreferenceStore> { AndroidBooleanPreferenceStore(get()) }
    single<StringPreferenceStore> { AndroidStringPreferenceStore(get()) }

    single<DocumentTextExtractor> { AndroidDocumentTextExtractor }

    single<ExternalUriOpener> { AndroidExternalUriOpener(get()) }
    single<OAuthCallbackSessionFactory> {
        AndroidOAuthCallbackSessionFactory(
            appScope = get(),
            appEventBus = get(),
            uriOpener = get(),
        )
    }

    single {
        LocalTools(
            eventBus = get(),
            settingsStore = get(),
            ttsManager = get(),
            platformTools = AndroidLocalTools(context = get(), eventBus = get()),
        )
    }

    single<AnalyticsTracker> { AndroidFirebaseAnalyticsTracker(Firebase.analytics) }
    single<CrashReporter> { AndroidFirebaseCrashReporter(Firebase.crashlytics) }

    single {
        SoundEffectPlayer(get())
    }

    single {
        val context = get<Context>()
        val appScope = get<AppScope>()
        val chatService = get<ChatService>()
        val conversationRepo = get<ConversationRepository>()
        val folderRepo = get<FolderRepository>()
        val settingsStore = get<SettingsStore>()
        val filesManager = get<FilesManager>()
        WebServerManager(
            appScope = appScope,
            host = KtorWebServerHost {
                configureWebApi(context.filesDir, chatService, conversationRepo, folderRepo, settingsStore, filesManager) {
                    context.assets.open(it)
                }
            },
            nsdRegistrar = AndroidJmDnsServiceRegistrar(context),
        )
    }
    single<WebServerRuntime> {
        AndroidWebServerRuntime(
            context = get(),
            manager = get(),
            settingsStore = get(),
            scope = get<AppScope>(),
        )
    }

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

    single { MessageFtsManager(get(), MessageFtsDialect.SIMPLE) }

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

    single { FilesManager(repository = get(), appScope = get()) }

    single {
        val context: Context = get()
        WorkspaceManager(
            baseDir = File(context.filesDir, "workspaces"),
            shellRunner = ProotShellRunner(
                nativeLibraryDir = File(context.applicationInfo.nativeLibraryDir),
            ),
            // 同一份挂载表既用于 PRoot 的 -b 参数, 也用于文件工具的路径解析, 避免两处漂移
            bindMounts = listOf(
                WorkspaceBindMount(
                    source = File(context.filesDir, FileFolders.SKILLS).apply { mkdirs() },
                    target = "/skills",
                ),
                WorkspaceBindMount(
                    source = File(context.filesDir, FileFolders.TOOL_OUTPUTS).apply { mkdirs() },
                    target = "/tool_outputs",
                ),
                WorkspaceBindMount(
                    source = File(context.filesDir, FileFolders.UPLOAD).apply { mkdirs() },
                    target = "/upload",
                ),
            ),
        )
    }

    single {
        RootfsInstaller(get())
    }

    single {
        WorkspaceRepository(get(), get(), get(), get())
    }

    viewModelOf(::WorkspaceVM)
    viewModel<WorkspaceDetailVM> {
        WorkspaceDetailVM(
            id = it.get(),
            repository = get(),
        )
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
    )
}
