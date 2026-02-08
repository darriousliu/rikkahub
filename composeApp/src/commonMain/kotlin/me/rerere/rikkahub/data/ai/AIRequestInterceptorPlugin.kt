package me.rerere.rikkahub.data.ai

import dev.gitlive.firebase.remoteconfig.FirebaseRemoteConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.util.AttributeKey

class AIRequestInterceptorPlugin(private val remoteConfig: FirebaseRemoteConfig) {
    companion object : HttpClientPlugin<AIRequestConfig, AIRequestInterceptorPlugin> {
        override val key = AttributeKey<AIRequestInterceptorPlugin>("AIRequestInterceptorPlugin")

        override fun prepare(block: AIRequestConfig.() -> Unit): AIRequestInterceptorPlugin {
            val config = AIRequestConfig().apply(block)
            return AIRequestInterceptorPlugin(config.remoteConfig)
        }

        override fun install(plugin: AIRequestInterceptorPlugin, scope: HttpClient) {
            scope.plugin(HttpSend).intercept { request ->
                val host = request.url.host

//                if (host == "api.siliconflow.cn") {
//                    plugin.processSiliconCloudRequest(request)
//                }

                // 继续执行请求
                execute(request)
            }
        }
    }


    // 处理硅基流动的请求
//    private fun processSiliconCloudRequest(request: HttpRequestBuilder) {
//        val authHeader = request.headers["Authorization"]
//        val path = request.url.encodedPath
//
//        // 如果没有设置api token, 填入免费api key
//        if ((authHeader?.trim() == "Bearer" || authHeader?.trim() == "Bearer sk-") && path in listOf(
//                "/v1/chat/completions",
//                "/v1/models"
//            )
//        ) {
//            val bodyJson = request.readBodyAsJson()
//            val model = bodyJson?.jsonObject?.get("model")?.jsonPrimitiveOrNull?.content
//            val freeModels = remoteConfig.get<String>("silicon_cloud_free_models").split(",")
//
//            if (model.isNullOrEmpty() || model in freeModels) {
//                val apiKey = remoteConfig.get<String>("silicon_cloud_api_key").decodeBase64String()
//                request.headers["Authorization"] = "Bearer $apiKey"
//            }
//        }
//    }

}

class AIRequestConfig {
    lateinit var remoteConfig: FirebaseRemoteConfig
}

//private fun HttpRequestBuilder.readBodyAsJson(): JsonElement? {
//    val body = this.body
//
//    return when (body) {
//        is TextContent -> {
//            if (body.contentType.match(ContentType.Application.Json)) {
//                try {
//                    JsonInstant.parseToJsonElement(body.text)
//                } catch (e: Exception) {
//                    null
//                }
//            } else null
//        }
//
//        is ByteArrayContent -> {
//            if (body.contentType?.match(ContentType.Application.Json) == true) {
//                try {
//                    val text = body.bytes().decodeToString()
//                    JsonInstant.parseToJsonElement(text)
//                } catch (e: Exception) {
//                    null
//                }
//            } else null
//        }
//
//        else -> null
//    }
//}

