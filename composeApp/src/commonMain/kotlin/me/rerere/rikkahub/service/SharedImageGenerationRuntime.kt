package me.rerere.rikkahub.service

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.ImageFormat
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.compressImage
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import io.github.vinceglb.filekit.div
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import me.rerere.ai.provider.ImageEditParams
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.db.dao.GenMediaDAO
import me.rerere.rikkahub.data.db.entity.GenMediaEntity
import me.rerere.rikkahub.ui.pages.imggen.GeneratedImage

internal class SharedImageGenerationRuntime(
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
    private val genMediaDao: GenMediaDAO,
) : ImageGenerationRuntime {
    override val settingsFlow: StateFlow<Settings> = settingsStore.settingsFlow

    private val imagesDirectory = FileKit.filesDir / IMAGES_DIRECTORY
    private val previewDirectory = FileKit.cacheDir / PREVIEW_DIRECTORY

    override fun generatedImages(): Flow<PagingData<GeneratedImage>> = Pager(
        config = PagingConfig(pageSize = 20, enablePlaceholders = false),
        pagingSourceFactory = genMediaDao::getAll,
    ).flow.map { pagingData ->
        pagingData.map(::toGeneratedImage)
    }

    override suspend fun updateSettings(settings: Settings) = settingsStore.update(settings)

    override suspend fun importReferenceImages(files: List<PlatformFile>): List<String> {
        previewDirectory.createDirectories()
        return files.mapNotNull { source ->
            try {
                val bytes = FileKit.compressImage(
                    bytes = source.readBytes(),
                    imageFormat = ImageFormat.PNG,
                    quality = 100,
                    maxWidth = MAX_REFERENCE_IMAGE_SIZE,
                    maxHeight = MAX_REFERENCE_IMAGE_SIZE,
                )
                val destination = previewDirectory / "imggen_ref_${randomFileSuffix()}.png"
                destination write bytes
                destination.absolutePath()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                null
            }
        }
    }

    override suspend fun deleteTemporaryFiles(paths: List<String>) {
        paths.forEach { path ->
            try {
                PlatformFile(path).delete(mustExist = false)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
            }
        }
    }

    override fun generateImage(request: ImageGenerationRequest): Flow<ImageGenerationUpdate> = flow {
        val (model, provider) = selectedModelAndProvider()
        val images = providerManager.getProviderByType(provider).generateImage(
            provider,
            ImageGenerationParams(
                model = model,
                prompt = request.prompt,
                numOfImages = request.numberOfImages,
                size = request.size,
                customHeaders = model.customHeaders,
                customBody = model.customBodies,
            ),
        )
        collectAndStore(
            images = images,
            request = request,
            modelName = model.displayName,
            type = GenMediaEntity.TYPE_IMAGE_GENERATION,
            emitUpdate = { emit(it) },
        )
    }

    override fun editImage(request: ImageGenerationRequest): Flow<ImageGenerationUpdate> = flow {
        val (model, provider) = selectedModelAndProvider()
        val images = providerManager.getProviderByType(provider).editImage(
            provider,
            ImageEditParams(
                model = model,
                prompt = request.prompt,
                images = request.referenceImages,
                numOfImages = request.numberOfImages,
                size = request.size,
                customHeaders = model.customHeaders,
                customBody = model.customBodies,
            ),
        )
        collectAndStore(
            images = images,
            request = request,
            modelName = model.displayName,
            type = GenMediaEntity.TYPE_IMAGE_EDIT,
            sourcePaths = request.referenceImages.joinToString("\n"),
            emitUpdate = { emit(it) },
        )
    }

    override suspend fun deleteImage(image: GeneratedImage) {
        genMediaDao.delete(image.id)
        PlatformFile(image.filePath).delete(mustExist = false)
    }

    private fun selectedModelAndProvider(): Pair<Model, ProviderSetting> {
        val settings = settingsStore.settingsFlow.value
        val model = settings.findModelById(settings.imageGenerationModelId)
            ?: error("No model selected")
        val resolvedProvider = model.findProvider(settings.providers)
            ?: error("Provider not found")
        val provider = settings.providers.find { it.id == resolvedProvider.id }
            ?: error("Provider setting not found")
        return model to provider
    }

    private suspend fun collectAndStore(
        images: Flow<ImageGenerationItem>,
        request: ImageGenerationRequest,
        modelName: String,
        type: String,
        sourcePaths: String? = null,
        emitUpdate: suspend (ImageGenerationUpdate) -> Unit,
    ) {
        var previewFile: PlatformFile? = null
        var finalIndex = 0
        try {
            images.collect { item ->
                if (item.partial) {
                    previewFile?.delete(mustExist = false)
                    previewFile = savePreview(item, item.partialImageIndex ?: finalIndex)
                    emitUpdate(
                        ImageGenerationUpdate(
                            image = previewFile!!.toGeneratedImage(request.prompt, modelName),
                            partial = true,
                        ),
                    )
                } else {
                    previewFile?.delete(mustExist = false)
                    previewFile = null
                    val finalFile = saveFinalImage(
                        item = item,
                        request = request,
                        modelName = modelName,
                        index = finalIndex,
                        type = type,
                        sourcePaths = sourcePaths,
                    )
                    finalIndex++
                    emitUpdate(
                        ImageGenerationUpdate(
                            image = finalFile.toGeneratedImage(request.prompt, modelName),
                            partial = false,
                        ),
                    )
                }
            }
        } finally {
            previewFile?.delete(mustExist = false)
        }
    }

    private suspend fun savePreview(item: ImageGenerationItem, index: Int): PlatformFile {
        previewDirectory.createDirectories()
        val file = previewDirectory / "imggen_${randomFileSuffix()}_$index.png"
        file write decodeImage(item.data)
        return file
    }

    private suspend fun saveFinalImage(
        item: ImageGenerationItem,
        request: ImageGenerationRequest,
        modelName: String,
        index: Int,
        type: String,
        sourcePaths: String?,
    ): PlatformFile {
        imagesDirectory.createDirectories()
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val fileName = "${timestamp}_${randomFileSuffix()}_$index.png"
        val file = imagesDirectory / fileName
        file write decodeImage(item.data)
        genMediaDao.insert(
            GenMediaEntity(
                path = "$IMAGES_DIRECTORY/$fileName",
                modelId = modelName,
                prompt = request.prompt,
                createAt = timestamp,
                type = type,
                sourcePaths = sourcePaths,
            ),
        )
        return file
    }

    private fun toGeneratedImage(entity: GenMediaEntity): GeneratedImage = GeneratedImage(
        id = entity.id,
        prompt = entity.prompt,
        filePath = (FileKit.filesDir / entity.path).absolutePath(),
        timestamp = entity.createAt,
        model = entity.modelId,
    )

    private fun PlatformFile.toGeneratedImage(prompt: String, modelName: String): GeneratedImage = GeneratedImage(
        id = 0,
        prompt = prompt,
        filePath = absolutePath(),
        timestamp = Clock.System.now().toEpochMilliseconds(),
        model = modelName,
    )

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeImage(data: String): ByteArray = Base64.decode(
        if (data.startsWith("data:image")) data.substringAfter("base64,") else data,
    )

    private fun randomFileSuffix(): String = Uuid.random().toString()

    private companion object {
        const val IMAGES_DIRECTORY = "images"
        const val PREVIEW_DIRECTORY = "imggen"
        const val MAX_REFERENCE_IMAGE_SIZE = 2048
    }
}
