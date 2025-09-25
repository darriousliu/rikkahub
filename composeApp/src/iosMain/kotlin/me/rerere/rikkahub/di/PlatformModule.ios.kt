package me.rerere.rikkahub.di

import androidx.room.Room
import me.rerere.common.PlatformContext
import me.rerere.common.utils.PlatformPebbleEngine
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.Migration_6_7
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { PlatformContext.INSTANCE } bind PlatformContext::class

    single {
        Room.databaseBuilder<AppDatabase>("rikka_hub")
            .addMigrations(Migration_6_7)
            .build()
    }

    single {
        PlatformPebbleEngine()
    }
}
