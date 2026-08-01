package me.rerere.ai.provider.providers

import okhttp3.OkHttpClient

internal fun googleProviderForTest(): GoogleProvider = GoogleProvider(OkHttpClient())
