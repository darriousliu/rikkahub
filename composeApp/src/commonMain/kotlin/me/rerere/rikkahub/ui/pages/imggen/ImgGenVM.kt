package me.rerere.rikkahub.ui.pages.imggen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import io.github.vinceglb.filekit.PlatformFile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.rerere.ai.ui.ImageGenSize
import me.rerere.common.logging.RikkaLog as Log
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.service.ImageGenerationRequest
import me.rerere.rikkahub.service.ImageGenerationRuntime

@Serializable
data class GeneratedImage(
    val id: Int,
    val prompt: String,
    val filePath: String,
    val timestamp: Long,
    val model: String,
)

class ImgGenVM(
    private val runtime: ImageGenerationRuntime,
) : ViewModel() {
    val settings: StateFlow<Settings> = runtime.settingsFlow

    private val _prompt = MutableStateFlow("")
    val prompt: StateFlow<String> = _prompt

    private val _numberOfImages = MutableStateFlow(1)
    val numberOfImages: StateFlow<Int> = _numberOfImages

    private val _size = MutableStateFlow(ImageGenSize.AUTO.value)
    val size: StateFlow<String> = _size

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating
    private var generationJob: Job? = null

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _currentGeneratedImages = MutableStateFlow<List<GeneratedImage>>(emptyList())
    val currentGeneratedImages: StateFlow<List<GeneratedImage>> = _currentGeneratedImages

    private val _referenceImages = MutableStateFlow<List<String>>(emptyList())
    val referenceImages: StateFlow<List<String>> = _referenceImages

    val generatedImages: Flow<PagingData<GeneratedImage>> = runtime.generatedImages().cachedIn(viewModelScope)

    fun updatePrompt(prompt: String) {
        _prompt.value = prompt
    }

    fun updateNumberOfImages(count: Int) {
        _numberOfImages.value = count.coerceIn(1, 4)
    }

    fun updateSize(size: String) {
        _size.value = size
    }

    fun updateImageGenerationModel(modelId: Uuid) {
        viewModelScope.launch {
            runtime.updateSettings(settings.value.copy(imageGenerationModelId = modelId))
        }
    }

    fun importReferenceImages(files: List<PlatformFile>) {
        if (files.isEmpty()) return
        viewModelScope.launch {
            runCatching { runtime.importReferenceImages(files) }
                .onSuccess { paths ->
                    _referenceImages.value = (_referenceImages.value + paths)
                        .distinct()
                        .take(MAX_REFERENCE_IMAGES)
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to import reference images", error)
                    _error.value = error.message ?: "Failed to import reference images"
                }
        }
    }

    fun removeReferenceImage(path: String) {
        _referenceImages.value = _referenceImages.value.filterNot { it == path }
        viewModelScope.launch { runtime.deleteTemporaryFiles(listOf(path)) }
    }

    fun clearReferenceImages() {
        val paths = _referenceImages.value
        _referenceImages.value = emptyList()
        viewModelScope.launch { runtime.deleteTemporaryFiles(paths) }
    }

    fun clearError() {
        _error.value = null
    }

    fun startNewSession() {
        generationJob?.cancel()
        clearReferenceImages()
        _prompt.value = ""
        _currentGeneratedImages.value = emptyList()
        _error.value = null
        _isGenerating.value = false
    }

    fun generateImage() {
        if (prompt.value.isBlank()) return
        startGeneration(edit = false)
    }

    fun editImage() {
        if (prompt.value.isBlank() || referenceImages.value.isEmpty()) return
        startGeneration(edit = true)
    }

    fun cancelGeneration() {
        generationJob?.cancel()
    }

    fun deleteImage(image: GeneratedImage) {
        viewModelScope.launch {
            runCatching { runtime.deleteImage(image) }
                .onFailure { error ->
                    Log.e(TAG, "Failed to delete image", error)
                    _error.value = "Failed to delete image"
                }
        }
    }

    private fun startGeneration(edit: Boolean) {
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            try {
                _isGenerating.value = true
                _error.value = null
                _currentGeneratedImages.value = emptyList()
                val request = ImageGenerationRequest(
                    prompt = prompt.value,
                    numberOfImages = numberOfImages.value,
                    size = size.value,
                    referenceImages = referenceImages.value,
                )
                val finalImages = mutableListOf<GeneratedImage>()
                val updates = if (edit) runtime.editImage(request) else runtime.generateImage(request)
                updates.collect { update ->
                    if (update.partial) {
                        _currentGeneratedImages.value = finalImages + update.image
                    } else {
                        finalImages += update.image
                        _currentGeneratedImages.value = finalImages.toList()
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) return@launch
                Log.e(TAG, "Failed to generate image", error)
                _error.value = error.message ?: "Unknown error occurred"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    private companion object {
        const val TAG = "ImgGenVM"
        const val MAX_REFERENCE_IMAGES = 16
    }
}
