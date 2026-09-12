package me.rerere.rikkahub.di

import korlibs.template.KorteTemplates
import kotlinx.coroutines.CoroutineScope
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.transformers.AssistantTemplateLoader
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.db.AppDatabase
import org.koin.core.qualifier.named
import org.koin.dsl.module

val dataSourceModule = module {
    single { AssistantTemplateLoader(settingsStore = get()) }
    single {
        val engine = get<KorteTemplates>()
        val loader = get<AssistantTemplateLoader>()
        engine.root = loader
        engine.includes = loader
        engine.layouts = loader
        TemplateTransformer(engine = engine)
    }

    single {
        get<AppDatabase>().conversationDao()
    }

    single {
        get<AppDatabase>().memoryDao()
    }

    single {
        get<AppDatabase>().genMediaDao()
    }

    single {
        get<AppDatabase>().messageNodeDao()
    }

    single {
        get<AppDatabase>().managedFileDao()
    }

    single {
        get<AppDatabase>().favoriteDao()
    }

    single {
        get<AppDatabase>().workspaceDao()
    }

    single {
        get<AppDatabase>().folderDao()
    }

    single {
        McpManager(
            settingsStore = get(),
            appScope = get<CoroutineScope>(),
            imageStore = get(),
            callbackSessionFactory = get(),
        )
    }
    single {
        GenerationHandler(
            filesDir = get(named("filesDir")),
            providerManager = get(),
            json = get(),
            memoryRepo = get(),
        )
    }
}
