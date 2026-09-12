package me.rerere.ai.provider.providers

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.BalanceOption
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.EmbeddingGenerationParams
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.common.text.formatFixedDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OpenAIProviderContractTest {
    @Test
    fun `models balance and embeddings keep original header scope while images retain referrals`() = runTest {
        for (host in listOf("openrouter.ai", "aihubmix.com")) {
            val requests = mutableListOf<HttpRequestData>()
            val http = HttpClient(MockEngine { request ->
                requests += request
                respond(when (request.url.encodedPath) {
                    "/v1/models" -> """{"data":[{"id":"model-a"},{}]}"""
                    "/v1/credits" -> """{"data":{"total_usage":0.125}}"""
                    "/v1/embeddings" -> """{"data":[{"embedding":[0.25,-0.5]}]}"""
                    else -> """{"data":[{"b64_json":"Zml4dHVyZQ=="}]}"""
                })
            })
            try {
                val provider = OpenAIProvider(http)
                val setting = ProviderSetting.OpenAI(apiKey = "fixture-key", baseUrl = "https://$host/v1")
                assertEquals(listOf("model-a"), provider.listModels(setting).map { it.modelId })
                assertEquals(formatFixedDecimal(0.125, 2), provider.getBalance(setting))
                val embedding = provider.generateEmbedding(setting, EmbeddingGenerationParams(
                    model = Model(modelId = "embedding-model"), input = listOf("hello"), dimensions = 2,
                    customHeaders = listOf(CustomHeader("X-Custom", "preserved"), CustomHeader("", "ignored")),
                ))
                assertEquals(listOf(listOf(0.25f, -0.5f)), embedding.embeddings)
                assertEquals("embedding-model", embedding.model)
                assertEquals(1, provider.generateImage(setting, ImageGenerationParams(
                    model = Model(modelId = "image-model"), prompt = "fixture",
                )).toList().size)
                requests.forEach { assertEquals("Bearer fixture-key", it.headers["Authorization"]) }
                for (request in requests.take(3)) {
                    listOf("HTTP-Referer", "X-Title", "APP-Code").forEach { assertNull(request.headers[it]) }
                }
                val requestBody = Json.parseToJsonElement(requests[2].body.toByteArray().decodeToString()).jsonObject
                assertEquals("hello", requestBody["input"]?.jsonPrimitive?.content)
                assertEquals("2", requestBody["dimensions"]?.jsonPrimitive?.content)
                assertEquals("preserved", requests[2].headers["X-Custom"])
                val imageHeaders = requests.last().headers
                if (host == "openrouter.ai") {
                    assertEquals("RikkaHub", imageHeaders["X-Title"])
                    assertEquals("https://rikka-ai.com", imageHeaders["HTTP-Referer"])
                } else {
                    assertEquals("DKHA9468", imageHeaders["APP-Code"])
                }
            } finally {
                http.close()
            }
        }
    }

    @Test
    fun `balance keeps absolute endpoint text values and original HTTP errors`() = runTest {
        val http = HttpClient(MockEngine { request ->
            assertEquals("https://balance.invalid/account", request.url.toString())
            assertEquals("Bearer fixture-key", request.headers["Authorization"])
            assertNull(request.headers["X-Title"])
            respond("""{"balance":"unlimited"}""")
        })
        val setting = ProviderSetting.OpenAI(
            apiKey = "fixture-key", baseUrl = "https://openrouter.ai/v1",
            balanceOption = BalanceOption(apiPath = "https://balance.invalid/account", resultPath = "balance"),
        )
        try {
            assertEquals("unlimited", OpenAIProvider(http).getBalance(setting))
        } finally {
            http.close()
        }
        val failing = HttpClient(MockEngine { respond("fixture error", HttpStatusCode.Forbidden) })
        try {
            val error = assertFailsWith<IllegalStateException> { OpenAIProvider(failing).getBalance(setting) }
            assertEquals("Failed to get balance: 403 fixture error", error.message)
        } finally {
            failing.close()
        }
    }
}
