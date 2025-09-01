package me.rerere.rikkahub.di

import androidx.room.Room
import io.pebbletemplates.pebble.PebbleEngine
import me.rerere.common.utils.PlatformPebbleEngine
import me.rerere.rikkahub.data.ai.transformers.AssistantTemplateLoader
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.Migration_6_7
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.Locale

actual val platformModule: Module = module {
    single {
        Room.databaseBuilder(get(), AppDatabase::class.java, "rikka_hub")
            .addMigrations(Migration_6_7)
            .build()
    }

    single {
        AssistantTemplateLoader(settingsStore = get())
    }

    single {
        PlatformPebbleEngine(get())
    }

    single {
        PebbleEngine.Builder()
            .loader(get<AssistantTemplateLoader>())
            .defaultLocale(Locale.getDefault())
            .autoEscaping(false)
            .build()
    }

    single { TemplateTransformer(engine = get(), settingsStore = get()) }
}
