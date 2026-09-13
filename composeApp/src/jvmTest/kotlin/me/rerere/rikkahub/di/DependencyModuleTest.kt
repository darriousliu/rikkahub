package me.rerere.rikkahub.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import me.rerere.rikkahub.data.api.SponsorAPI
import me.rerere.rikkahub.data.model.Sponsor
import me.rerere.rikkahub.shared.PlatformBuildInfo
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.utils.UpdateChecker
import me.rerere.rikkahub.utils.UpdateInfo
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class DependencyModuleTest {
    @Test
    fun commonRegistrationsUseTheSuppliedClientAndBuildInfo() = runTest {
        val requests = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            requests += request.url.toString()
            val body = when (request.url.host) {
                "updates.rikka-ai.com" -> {
                    assertEquals("RikkaHub 2.4.5 #245", request.headers[HttpHeaders.UserAgent])
                    """{"version":"2.4.5","publishedAt":"2026-09-13","changelog":"CMP50","downloads":[]}"""
                }
                "sponsors.rikka-ai.com" -> """[{"userName":"CMP50","avatar":"avatar","amount":"1"}]"""
                else -> error("Unexpected request: ${request.url}")
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val application = koinApplication {
            modules(appModule, dataSourceModule, module {
                single { client }
                single { PlatformBuildInfo("2.4.5", "245", true, "test", "test") }
            })
        }
        try {
            val checker = application.koin.get<UpdateChecker>()
            val sponsors = application.koin.get<SponsorAPI>()
            assertSame(checker, application.koin.get<UpdateChecker>())
            assertSame(sponsors, application.koin.get<SponsorAPI>())
            val states = checker.checkUpdate().toList()
            assertEquals(2, states.size)
            assertIs<UiState.Loading>(states.first())
            assertEquals("CMP50", assertIs<UiState.Success<UpdateInfo>>(states.last()).data.changelog)
            assertEquals(listOf(Sponsor("CMP50", "avatar", "1")), sponsors.getSponsors())
            assertEquals(listOf("https://updates.rikka-ai.com/", "https://sponsors.rikka-ai.com/sponsors"), requests)
        } finally {
            application.close()
            client.close()
        }
    }
}
