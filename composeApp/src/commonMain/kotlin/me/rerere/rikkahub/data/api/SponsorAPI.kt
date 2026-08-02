package me.rerere.rikkahub.data.api

import de.jensklingenberg.ktorfit.Ktorfit
import de.jensklingenberg.ktorfit.http.GET
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import me.rerere.rikkahub.data.model.Sponsor
import me.rerere.rikkahub.utils.JsonInstant

public interface SponsorAPI {
    @GET("sponsors")
    suspend fun getSponsors(): List<Sponsor>

    companion object {
        public fun create(httpClient: HttpClient): SponsorAPI {
            return create("https://sponsors.rikka-ai.com/", httpClient)
        }

        internal fun create(
            baseUrl: String,
            httpClient: HttpClient,
        ): SponsorAPI {
            val configuredClient = httpClient.config {
                expectSuccess = true
                install(ContentNegotiation) {
                    json(JsonInstant)
                }
            }
            return Ktorfit.Builder()
                .baseUrl(baseUrl)
                .httpClient(configuredClient)
                .build()
                .createSponsorAPI()
        }
    }
}
