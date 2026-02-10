package me.rerere.rikkahub.data.api

import de.jensklingenberg.ktorfit.Ktorfit
import de.jensklingenberg.ktorfit.http.GET
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import me.rerere.rikkahub.data.model.Sponsor
import me.rerere.rikkahub.utils.JsonInstant


interface SponsorAPI {
    @GET("/sponsors")
    suspend fun getSponsors(): List<Sponsor>

    companion object {
        fun create(httpClient: HttpClient): SponsorAPI {
            return Ktorfit.Builder()
                .httpClient(httpClient.config {
                    install(ContentNegotiation) {
                        json(JsonInstant)
                    }
                })
                .baseUrl("https://sponsors.rikka-ai.com/")
                .converterFactories()
                .build()
                .createSponsorAPI()
        }
    }
}
