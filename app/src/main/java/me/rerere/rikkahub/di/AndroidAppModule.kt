package me.rerere.rikkahub.di

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.crashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.io.files.Path
import me.rerere.ai.core.Tool
import me.rerere.common.android.appTempFolder
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.tools.local.AndroidLocalTools
import me.rerere.rikkahub.data.ai.tools.local.LocalTools
import me.rerere.rikkahub.data.ai.transformers.AndroidDocumentTextExtractor
import me.rerere.rikkahub.data.ai.transformers.DocumentTextExtractor
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.WorkspaceReminderTransformer
import me.rerere.rikkahub.data.datastore.AndroidBooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.AndroidStringPreferenceStore
import me.rerere.rikkahub.data.datastore.BooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.StringPreferenceStore
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.platform.AnalyticsTracker
import me.rerere.rikkahub.platform.AndroidExternalUriOpener
import me.rerere.rikkahub.platform.AndroidFirebaseAnalyticsTracker
import me.rerere.rikkahub.platform.AndroidFirebaseCrashReporter
import me.rerere.rikkahub.platform.AndroidJmDnsServiceRegistrar
import me.rerere.rikkahub.platform.AndroidOAuthCallbackSessionFactory
import me.rerere.rikkahub.platform.CrashReporter
import me.rerere.rikkahub.platform.ExternalUriOpener
import me.rerere.rikkahub.platform.OAuthCallbackSessionFactory
import me.rerere.rikkahub.service.AndroidChatNotificationPresenter
import me.rerere.rikkahub.service.ChatNotificationManager
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.createWorkspaceToolsIfReady
import me.rerere.rikkahub.shared.PlatformBuildInfo
import me.rerere.rikkahub.shared.createPlatformBuildInfo
import me.rerere.rikkahub.ui.theme.AndroidChatFontRuntime
import me.rerere.rikkahub.ui.theme.ChatFontRuntime
import me.rerere.rikkahub.utils.SoundEffectPlayer
import me.rerere.rikkahub.web.AndroidWebServerRuntime
import me.rerere.rikkahub.web.KtorWebServerHost
import me.rerere.rikkahub.web.WebServerManager
import me.rerere.rikkahub.web.WebServerRuntime
import me.rerere.rikkahub.web.configureWebApi
import me.rerere.tts.provider.TTSManager
import me.rerere.tts.provider.providers.SystemTTSProvider
import org.koin.core.qualifier.named
import org.koin.dsl.module

val androidAppModule = module {
    includes(appModule)
    single<CoroutineScope> { get<AppScope>() }
    single<Path>(named("filesDir")) {
        Path(get<Context>().filesDir.path)
    }
    single<Path>(named("cacheDir")) {
        Path(get<Context>().appTempFolder.path)
    }
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
    single<ChatFontRuntime> { AndroidChatFontRuntime(get()) }

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
        AppEventBus()
    }

    single {
        LocalTools(
            eventBus = get(),
            settingsStore = get(),
            ttsManager = get(),
            platformTools = AndroidLocalTools(context = get(), eventBus = get()),
        )
    }

    single {
        AppScope()
    }

    single {
        TTSManager(
            httpClient = get(),
            systemProvider = SystemTTSProvider(get()),
        )
    }

    single<AnalyticsTracker> { AndroidFirebaseAnalyticsTracker(Firebase.analytics) }
    single<CrashReporter> { AndroidFirebaseCrashReporter(Firebase.crashlytics) }

    single {
        SoundEffectPlayer(get())
    }

    // 生成通知与业务解耦：ChatService 只发事件，通知由这里消费；
    // createdAtStart 保证进程启动即订阅，否则后台生成的事件会因无订阅者而丢失
    single(createdAtStart = true) {
        ChatNotificationManager(
            appScope = get<AppScope>(),
            presenter = AndroidChatNotificationPresenter(get()),
            settingsStore = get(),
            eventBus = get(),
        )
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
                configureWebApi(context, chatService, conversationRepo, folderRepo, settingsStore, filesManager)
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
}
