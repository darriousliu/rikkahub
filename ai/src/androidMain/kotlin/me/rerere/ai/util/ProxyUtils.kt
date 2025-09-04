package me.rerere.ai.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import me.rerere.ai.provider.ProviderProxy
import okio.ByteString.Companion.encode

private const val TAG = "ProxyUtils"



fun HttpClient.configureClientWithProxy(proxyConfig: ProviderProxy): HttpClient {
    return when (proxyConfig) {
        is ProviderProxy.None -> this

        is ProviderProxy.Http -> this.config {
            // 创建新的 HttpClient 配置
            // 复制原客户端的引擎配置
            engine {
                // 配置代理
                proxy = ProxyBuilder.http(
                    url = Url(
                        URLBuilder(
                            host = proxyConfig.address,
                            port = proxyConfig.port
                        )
                    ),
                )
            }

            // 如果有认证信息，安装 Auth 插件
            if (!proxyConfig.username.isNullOrEmpty() && !proxyConfig.password.isNullOrEmpty()) {
                install(ProxyAuthPlugin) {
                    username = proxyConfig.username
                    password = proxyConfig.password
                }
            }
        }
    }
}


/**
 * 自定义代理认证插件配置
 */
class ProxyAuthConfig {
    var username: String = ""
    var password: String = ""
}

/**
 * 自定义代理认证插件
 * 在每个请求中添加 Proxy-Authorization 头
 */
val ProxyAuthPlugin = createClientPlugin("ProxyAuth", ::ProxyAuthConfig) {
    val username = pluginConfig.username
    val password = pluginConfig.password

    // 生成 Basic 认证凭据
    val credentials = "Basic " + "$username:$password".encode(Charsets.ISO_8859_1).base64()

    onRequest { request, _ ->
        // 为每个请求添加 Proxy-Authorization 头
        request.headers {
            append(HttpHeaders.ProxyAuthorization, credentials)
        }
    }
}


