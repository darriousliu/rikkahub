package me.rerere.rikkahub.ui.pages.assistant.detail

import android.content.Context
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.ai.transformers.TransformerContext
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant

class AndroidAssistantPromptPreviewRuntime(
    private val context: Context,
    private val templateTransformer: TemplateTransformer,
) : AssistantPromptPreviewRuntime {
    override suspend fun transform(
        assistant: Assistant,
        settings: Settings,
        messages: List<UIMessage>,
    ): List<UIMessage> = templateTransformer.transform(
        ctx = TransformerContext(
            context = context,
            model = Model(modelId = "gpt-4o", displayName = "GPT-4o"),
            assistant = assistant,
            settings = settings,
        ),
        messages = messages,
    )
}
