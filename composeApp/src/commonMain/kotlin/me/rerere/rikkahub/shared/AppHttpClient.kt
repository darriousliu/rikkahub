package me.rerere.rikkahub.shared

import io.ktor.client.HttpClient

internal expect fun createAppHttpClient(): HttpClient
