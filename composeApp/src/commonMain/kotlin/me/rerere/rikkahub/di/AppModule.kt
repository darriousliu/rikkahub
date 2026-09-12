package me.rerere.rikkahub.di

import kotlinx.serialization.json.Json
import me.rerere.ai.core.Tool
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.utils.JsonInstant
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appModule = module {
    single<Json> { JsonInstant }
    single {
        ChatService(
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
            folderRepository = get(),
            booleanPreferenceStore = get(),
            stringPreferenceStore = get(),
            workspaceReminderTransformer = getOrNull<InputMessageTransformer>(named("workspaceReminder")),
            workspaceTools = getOrNull<suspend (String?, String?) -> List<Tool>>(named("workspaceTools"))
                ?: { _, _ -> emptyList() },
        )
    }
}
