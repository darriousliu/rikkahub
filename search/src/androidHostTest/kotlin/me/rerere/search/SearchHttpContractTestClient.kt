package me.rerere.search

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

internal fun initializeSearchHttpClientForContractTest(serverUrl: String) {
    val destination = serverUrl.toHttpUrl()
    val redirectingClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val redirectedUrl = request.url.newBuilder()
                .scheme(destination.scheme)
                .host(destination.host)
                .port(destination.port)
                .build()
            chain.proceed(request.newBuilder().url(redirectedUrl).build())
        }
        .build()
    SearchService.init(HttpClient(OkHttp) {
        engine { preconfigured = redirectingClient }
    })
}
