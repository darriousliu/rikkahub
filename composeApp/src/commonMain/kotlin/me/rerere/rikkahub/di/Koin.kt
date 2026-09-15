package me.rerere.rikkahub.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin

fun initKoin(application: KoinApplication.() -> Unit): KoinApplication = startKoin {
    modules(commonModule)
    application()
}
