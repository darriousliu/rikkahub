package me.rerere.rikkahub.service

import androidx.paging.PagingData
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.pages.imggen.GeneratedImage

data class ImageGenerationRequest(
    val prompt: String,
    val numberOfImages: Int,
    val size: String,
    val referenceImages: List<String> = emptyList(),
)

data class ImageGenerationUpdate(
    val image: GeneratedImage,
    val partial: Boolean,
)

interface ImageGenerationRuntime {
    val settingsFlow: StateFlow<Settings>

    fun generatedImages(): Flow<PagingData<GeneratedImage>>

    suspend fun updateSettings(settings: Settings)

    suspend fun importReferenceImages(files: List<PlatformFile>): List<String>

    suspend fun deleteTemporaryFiles(paths: List<String>)

    fun generateImage(request: ImageGenerationRequest): Flow<ImageGenerationUpdate>

    fun editImage(request: ImageGenerationRequest): Flow<ImageGenerationUpdate>

    suspend fun deleteImage(image: GeneratedImage)
}
