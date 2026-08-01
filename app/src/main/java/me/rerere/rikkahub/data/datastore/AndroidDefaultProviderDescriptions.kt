package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.ProviderDescription
import me.rerere.rikkahub.generated.resources.Res
import me.rerere.rikkahub.generated.resources.silicon_flow_description
import me.rerere.rikkahub.generated.resources.silicon_flow_website
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.resources.stringResource
import kotlin.uuid.Uuid

internal val ANDROID_DEFAULT_PROVIDER_DESCRIPTIONS = mapOf(
    Uuid.parse("56a94d29-c88b-41c5-8e09-38a7612d6cf8") to ProviderDescription {
        MarkdownBlock(
            content = """
                ${stringResource(Res.string.silicon_flow_description)}
                ${stringResource(Res.string.silicon_flow_website)}
            """.trimIndent(),
        )
    },
    Uuid.parse("da020a90-f7b3-4c29-b90e-c511a0630630") to ProviderDescription {
        MarkdownBlock(
            content = """
                小马算力是一家提供国产模型的API网关服务，使用统一接口接入多种模型
                官网: [tokenpony.cn](https://www.tokenpony.cn/79clb)
            """.trimIndent(),
        )
    },
)
