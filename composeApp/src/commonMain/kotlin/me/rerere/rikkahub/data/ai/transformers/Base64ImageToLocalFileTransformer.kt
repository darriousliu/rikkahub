package me.rerere.rikkahub.data.ai.transformers

import kotlin.io.encoding.Base64
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.platform.FileKitPlatformFileStore
import me.rerere.rikkahub.platform.FileStoreArea
import me.rerere.rikkahub.platform.PlatformFileStore
import me.rerere.rikkahub.platform.encodeImageToPng
import me.rerere.rikkahub.service.toFileUri
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private const val BASE64_IMAGE_PREFIX = "data:image"
private const val STORED_IMAGE_NAME = "image.png"

/**
 * Persists inline base64 images so conversations never carry megabytes of data URIs.
 *
 * Images are normalized to PNG on the way in, which keeps every stored attachment in one format.
 */
fun interface Base64ImageStore {
    /** Returns the file uri of the stored image, or null when the payload is not a usable image. */
    suspend fun storeAsPng(bytes: ByteArray): String?
}

class SharedBase64ImageStore(
    private val fileStore: PlatformFileStore = FileKitPlatformFileStore(),
) : Base64ImageStore {
    override suspend fun storeAsPng(bytes: ByteArray): String? {
        val png = encodeImageToPng(bytes) ?: return null
        return fileStore
            .writeIntoSandbox(png, STORED_IMAGE_NAME, FileStoreArea.ATTACHMENTS)
            .getOrNull()
            ?.file
            ?.toFileUri()
    }
}

object Base64ImageToLocalFileTransformer : OutputMessageTransformer, KoinComponent {
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    override suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val store = get<Base64ImageStore>()
        return messages.map { message ->
            message.copy(
                parts = message.parts.map { part ->
                    if (part !is UIMessagePart.Image || !part.url.startsWith(BASE64_IMAGE_PREFIX)) {
                        return@map part
                    }
                    val decoded = runCatching {
                        Base64.decode(part.url.substringAfter("base64,"))
                    }.getOrNull() ?: return@map part
                    val storedUrl = runCatching { store.storeAsPng(decoded) }.getOrNull()
                    if (storedUrl == null) part else part.copy(url = storedUrl)
                },
            )
        }
    }
}
