package me.rerere.rikkahub.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import java.io.File
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.ImageEditParams
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.common.android.appTempFolder
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.db.entity.GenMediaEntity
import me.rerere.rikkahub.data.files.FileUtils
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.GenMediaRepository
import me.rerere.rikkahub.ui.pages.imggen.GeneratedImage
import me.rerere.rikkahub.utils.ImageUtils

class AndroidImageGenerationRuntime(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
    private val genMediaRepository: GenMediaRepository,
    private val filesManager: FilesManager,
) : ImageGenerationRuntime {
    override val settingsFlow: StateFlow<Settings> = settingsStore.settingsFlow

    override fun generatedImages(): Flow<PagingData<GeneratedImage>> = Pager(
        config = PagingConfig(pageSize = 20, enablePlaceholders = false),
        pagingSourceFactory = { genMediaRepository.getAllMedia() },
    ).flow.map { pagingData ->
        pagingData.map(::toGeneratedImage)
    }

    override suspend fun updateSettings(settings: Settings) {
        settingsStore.update(settings)
    }

    override suspend fun importReferenceImages(files: List<PlatformFile>): List<String> =
        withContext(Dispatchers.IO) {
            files.mapNotNull { source ->
                runCatching {
                    val sourceBytes = source.readBytes()
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, bounds)
                    val bitmap = BitmapFactory.decodeByteArray(
                        sourceBytes,
                        0,
                        sourceBytes.size,
                        BitmapFactory.Options().apply {
                            inSampleSize = ImageUtils.calculateInSampleSize(bounds, MAX_REFERENCE_IMAGE_SIZE, MAX_REFERENCE_IMAGE_SIZE)
                            inPreferredConfig = Bitmap.Config.RGB_565
                        },
                    ) ?: error("Failed to decode image")
                    val pngBytes = try {
                        FileUtils.compressBitmapToPng(bitmap)
                    } finally {
                        bitmap.recycle()
                    }
                    File(context.appTempFolder, "imggen_ref_${Uuid.random()}.png").apply {
                        writeBytes(pngBytes)
                    }.absolutePath
                }.getOrNull()
            }
        }

    override suspend fun deleteTemporaryFiles(paths: List<String>) {
        withContext(Dispatchers.IO) {
            paths.forEach { path -> File(path).delete() }
        }
    }

    override fun generateImage(request: ImageGenerationRequest): Flow<ImageGenerationUpdate> = flow {
        val settings = settingsStore.settingsFlow.value
        val model = settings.findModelById(settings.imageGenerationModelId)
            ?: error("No model selected")
        val provider = model.findProvider(settings.providers)
            ?: error("Provider not found")
        val providerSetting = settings.providers.find { it.id == provider.id }
            ?: error("Provider setting not found")
        val images = providerManager.getProviderByType(provider).generateImage(
            providerSetting,
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
        ) { emit(it) }
    }

    override fun editImage(request: ImageGenerationRequest): Flow<ImageGenerationUpdate> = flow {
        val settings = settingsStore.settingsFlow.value
        val model = settings.findModelById(settings.imageGenerationModelId)
            ?: error("No model selected")
        val provider = model.findProvider(settings.providers)
            ?: error("Provider not found")
        val providerSetting = settings.providers.find { it.id == provider.id }
            ?: error("Provider setting not found")
        val images = providerManager.getProviderByType(provider).editImage(
            providerSetting,
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
        ) { emit(it) }
    }

    override suspend fun deleteImage(image: GeneratedImage) {
        genMediaRepository.deleteMedia(image.id)
        withContext(Dispatchers.IO) {
            File(image.filePath).delete()
        }
    }

    private suspend fun collectAndStore(
        images: Flow<ImageGenerationItem>,
        request: ImageGenerationRequest,
        modelName: String,
        type: String,
        sourcePaths: String? = null,
        emitUpdate: suspend (ImageGenerationUpdate) -> Unit,
    ) {
        var previewFile: File? = null
        var finalIndex = 0
        try {
            images.collect { item ->
                if (item.partial) {
                    previewFile?.delete()
                    previewFile = savePreview(
                        item = item,
                        modelName = modelName,
                        index = item.partialImageIndex ?: finalIndex,
                    )
                    emitUpdate(
                        ImageGenerationUpdate(
                            image = previewFile!!.toGeneratedImage(request.prompt, modelName),
                            partial = true,
                        ),
                    )
                } else {
                    previewFile?.delete()
                    previewFile = null
                    val imageFile = saveFinalImage(
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
                            image = imageFile.toGeneratedImage(request.prompt, modelName),
                            partial = false,
                        ),
                    )
                }
            }
        } finally {
            previewFile?.delete()
        }
    }

    private fun savePreview(
        item: ImageGenerationItem,
        modelName: String,
        index: Int,
    ): File {
        val imageFile = File(
            context.appTempFolder,
            "imggen_${System.currentTimeMillis()}_${modelName}_$index.png",
        )
        return filesManager.createImageFileFromBase64(item.data, imageFile.absolutePath)
    }

    private suspend fun saveFinalImage(
        item: ImageGenerationItem,
        request: ImageGenerationRequest,
        modelName: String,
        index: Int,
        type: String,
        sourcePaths: String?,
    ): File {
        val timestamp = System.currentTimeMillis()
        val imageFile = File(filesManager.getImagesDir(), "${timestamp}_${modelName}_$index.png")
        val createdFile = filesManager.createImageFileFromBase64(item.data, imageFile.absolutePath)
        genMediaRepository.insertMedia(
            GenMediaEntity(
                path = "images/${imageFile.name}",
                modelId = modelName,
                prompt = request.prompt,
                createAt = timestamp,
                type = type,
                sourcePaths = sourcePaths,
            ),
        )
        return createdFile
    }

    private fun toGeneratedImage(entity: GenMediaEntity): GeneratedImage = GeneratedImage(
        id = entity.id,
        prompt = entity.prompt,
        filePath = File(filesManager.getImagesDir(), entity.path.removePrefix("images/")).absolutePath,
        timestamp = entity.createAt,
        model = entity.modelId,
    )

    private fun File.toGeneratedImage(prompt: String, modelName: String): GeneratedImage = GeneratedImage(
        id = 0,
        prompt = prompt,
        filePath = absolutePath,
        timestamp = System.currentTimeMillis(),
        model = modelName,
    )

    private companion object {
        const val MAX_REFERENCE_IMAGE_SIZE = 2048
    }
}
