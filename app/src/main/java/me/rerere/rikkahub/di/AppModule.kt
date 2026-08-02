package me.rerere.rikkahub.di

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.crashlytics
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.AndroidBooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.AndroidStringPreferenceStore
import me.rerere.rikkahub.data.datastore.BooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.StringPreferenceStore
import me.rerere.rikkahub.data.ai.tools.local.LocalTools
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.platform.AndroidExternalUriOpener
import me.rerere.rikkahub.platform.AndroidFirebaseAnalyticsTracker
import me.rerere.rikkahub.platform.AndroidFirebaseCrashReporter
import me.rerere.rikkahub.platform.AndroidOAuthCallbackSessionFactory
import me.rerere.rikkahub.platform.AnalyticsTracker
import me.rerere.rikkahub.platform.ChatNotificationManager
import me.rerere.rikkahub.platform.CrashReporter
import me.rerere.rikkahub.platform.ExternalUriOpener
import me.rerere.rikkahub.platform.OAuthCallbackSessionFactory
import me.rerere.rikkahub.service.AndroidChatNotificationPresenter
import me.rerere.rikkahub.service.AndroidImageGenerationRuntime
import me.rerere.rikkahub.service.AndroidTranslationRuntime
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.ChatRuntime
import me.rerere.rikkahub.service.ImageGenerationRuntime
import me.rerere.rikkahub.service.TranslationRuntime
import me.rerere.rikkahub.ui.pages.assistant.AndroidAssistantAssetCleaner
import me.rerere.rikkahub.ui.pages.assistant.AndroidAssistantSkillCatalog
import me.rerere.rikkahub.ui.pages.assistant.AssistantAssetCleaner
import me.rerere.rikkahub.ui.pages.assistant.AssistantSkillCatalog
import me.rerere.rikkahub.ui.theme.AndroidChatFontRuntime
import me.rerere.rikkahub.ui.theme.ChatFontRuntime
import me.rerere.rikkahub.shared.PlatformBuildInfo
import me.rerere.rikkahub.shared.createPlatformBuildInfo
import me.rerere.rikkahub.utils.EmojiData
import me.rerere.rikkahub.utils.EmojiUtils
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.SoundEffectPlayer
import me.rerere.rikkahub.utils.UpdateChecker
import me.rerere.rikkahub.web.WebServerManager
import me.rerere.rikkahub.web.AndroidWebServerRuntime
import me.rerere.rikkahub.web.WebServerRuntime
import me.rerere.tts.provider.TTSManager
import me.rerere.tts.provider.providers.SystemTTSProvider
import org.koin.dsl.module

val appModule = module {
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

    single<Json> { JsonInstant }
    single<BooleanPreferenceStore> { AndroidBooleanPreferenceStore(get()) }
    single<StringPreferenceStore> { AndroidStringPreferenceStore(get()) }
    single<ChatFontRuntime> { AndroidChatFontRuntime(get()) }

    single<AssistantAssetCleaner> { AndroidAssistantAssetCleaner(get()) }
    single<AssistantSkillCatalog> { AndroidAssistantSkillCatalog(get()) }

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
        LocalTools(get(), get(), get(), get())
    }

    single {
        UpdateChecker(client = get(), buildInfo = get())
    }

    single {
        AppScope()
    }

    single<EmojiData> {
        EmojiUtils.loadEmoji(get())
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
        ChatNotificationManager().apply {
            start(
                scope = get<AppScope>(),
                presenter = AndroidChatNotificationPresenter(get()),
                settingsStore = get(),
                eventBus = get(),
            )
        }
    }

    single {
        ChatService(
            context = get(),
            appScope = get(),
            appEventBus = get(),
            settingsStore = get(),
            conversationRepo = get(),
            memoryRepository = get(),
            generationHandler = get(),
            templateTransformer = get(),
            providerManager = get(),
            localTools = get(),
            mcpManager = get(),
            filesManager = get(),
            skillManager = get(),
            workspaceRepository = get(),
            folderRepository = get()
        )
    }
    single<ChatRuntime> { get<ChatService>() }
    single<TranslationRuntime> { AndroidTranslationRuntime(get(), get()) }
    single<ImageGenerationRuntime> {
        AndroidImageGenerationRuntime(
            context = get(),
            settingsStore = get(),
            providerManager = get(),
            genMediaRepository = get(),
            filesManager = get(),
        )
    }

    single {
        WebServerManager(
            context = get(),
            appScope = get(),
            chatService = get(),
            conversationRepo = get(),
            folderRepo = get(),
            settingsStore = get(),
            filesManager = get()
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
