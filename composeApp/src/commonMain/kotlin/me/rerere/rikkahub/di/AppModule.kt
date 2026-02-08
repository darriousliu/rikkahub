package me.rerere.rikkahub.di

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics
import dev.gitlive.firebase.crashlytics.crashlytics
import dev.gitlive.firebase.remoteconfig.remoteConfig
import kotlinx.serialization.json.Json
import me.rerere.highlight.Highlighter
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.AILoggingManager
import me.rerere.rikkahub.data.ai.tools.LocalTools
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.utils.EmojiData
import me.rerere.rikkahub.utils.EmojiUtils
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.UpdateChecker
import me.rerere.tts.provider.TTSManager
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module

val appModule = module {
    single<Json> { JsonInstant }

    single {
        Highlighter()
    }

    single {
        LocalTools(get())
    }

    single {
        UpdateChecker(get(qualifier(HttpClientType.Chat)))
    }

    single {
        AppScope()
    }

    single<EmojiData> {
        EmojiUtils.loadEmoji()
    }

    single {
        TTSManager(get())
    }

    single {
        Firebase.crashlytics
    }

    single {
        Firebase.remoteConfig
    }

    single {
        Firebase.analytics
    }

    single {
        AILoggingManager()
    }

    single {
        ChatService(
            context = get(),
            appScope = get(),
            settingsStore = get(),
            conversationRepo = get(),
            memoryRepository = get(),
            generationHandler = get(),
            templateTransformer = get(),
            providerManager = get(),
            localTools = get(),
            mcpManager = get(),
            filesManager = get()
        )
    }
}
