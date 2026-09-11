package me.rerere.rikkahub.ui.pages.imggen

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.vinceglb.filekit.PlatformFile
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.io.files.Path
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.ImageEditParams
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseConstructor
import me.rerere.rikkahub.data.db.buildAppDatabase
import me.rerere.rikkahub.data.db.dao.GenMediaDAO
import me.rerere.rikkahub.data.db.entity.GenMediaEntity
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.repository.GenMediaRepository
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import kotlin.io.encoding.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ImgGenVMContractTest {
    private lateinit var ui: ExecutorCoroutineDispatcher
    private val fixtures = mutableListOf<Fixture>()

    @BeforeTest
    fun setUp() {
        ui = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        Dispatchers.setMain(ui)
    }

    @AfterTest
    fun tearDown() {
        runBlocking(ui) { fixtures.forEach { it.close() } }
        Dispatchers.resetMain()
        ui.close()
    }

    @Test
    fun `generation preserves parameters replaces previews and persists final images with original names`() = scenario {
        val f = fixture()
        val vm = f.vm
        vm.updatePrompt("original prompt")
        vm.updateNumberOfImages(2)
        vm.updateSize("1536x1024")
        vm.generateImage()
        val call = f.provider.requests.receive()
        assertEquals(ImageGenerationParams(
            model = f.model, prompt = "original prompt", numOfImages = 2, size = "1536x1024",
            customHeaders = f.model.customHeaders, customBody = f.model.customBodies,
        ), call.generation)
        assertEquals(f.providerSetting, call.setting)
        vm.updatePrompt("later prompt")

        call.reply(partial = true, index = 7)
        val preview = File(vm.currentGeneratedImages.first { it.isNotEmpty() }.single().filePath)
        assertTrue(preview.name.matches(Regex("imggen_\\d+_Contract Model_7.png")))
        assertTrue(f.dao.getAllMedia().isEmpty())
        call.reply(partial = true, index = 8)
        val second = File(vm.currentGeneratedImages.first { it.single().filePath != preview.path }.single().filePath)
        assertFalse(preview.exists())
        assertTrue(second.exists())

        call.reply()
        vm.currentGeneratedImages.first { it.single().filePath.startsWith(f.images.path) }
        assertFalse(second.exists())
        call.reply(dataUri = true)
        call.responses.close()
        vm.isGenerating.first { !it }
        assertNull(vm.error.value)
        val rows = f.dao.getAllMedia().sortedBy { it.path.substringAfterLast('_') }
        assertEquals(2, rows.size)
        rows.forEachIndexed { index, row ->
            assertEquals("images/${row.createAt}_Contract Model_$index.png", row.path)
            assertEquals("original prompt", row.prompt)
            assertEquals("Contract Model", row.modelId)
            assertEquals(GenMediaEntity.TYPE_IMAGE_GENERATION, row.type)
            assertNull(row.sourcePaths)
            assertContentEquals(PNG, File(f.root, row.path).readBytes())
        }
        assertEquals(2, vm.currentGeneratedImages.value.size)
        assertTrue(vm.currentGeneratedImages.value.all { it.id == 0 && it.prompt == "original prompt" })
        assertEquals("later prompt", vm.prompt.value)
    }

    @Test
    fun `edit imports PNG references and preserves their order request options and source paths`() = scenario {
        val f = fixture()
        val source = File(f.root, "source.jpg")
        ImageIO.write(BufferedImage(2400, 1200, BufferedImage.TYPE_INT_RGB), "jpg", source)
        val invalid = File(f.root, "invalid.jpg").apply { writeText("not an image") }
        f.vm.importReferenceImages(listOf(PlatformFile(invalid), PlatformFile(source)))
        val imported = f.vm.referenceImages.first { it.isNotEmpty() }.single()
        val decoded = ImageIO.read(File(imported))
        assertTrue(decoded.width <= 2048 && decoded.height <= 2048)
        assertEquals("png", ImageIO.createImageInputStream(File(imported)).use {
            ImageIO.getImageReaders(it).next().formatName.lowercase()
        })
        val second = File(f.root, "second.png").apply { writeBytes(PNG) }.path
        f.vm.addReferenceImages(listOf(imported, second, imported))
        f.vm.updatePrompt("edit prompt")
        f.vm.updateNumberOfImages(3)
        f.vm.updateSize("1024x1024")
        f.vm.editImage()
        val call = f.provider.requests.receive()
        assertEquals(ImageEditParams(
            model = f.model, prompt = "edit prompt", images = listOf(imported, second),
            numOfImages = 3, size = "1024x1024",
            customHeaders = f.model.customHeaders, customBody = f.model.customBodies,
        ), call.edit)
        call.reply()
        call.responses.close()
        f.vm.isGenerating.first { !it }
        val row = f.dao.getAllMedia().single()
        assertEquals(GenMediaEntity.TYPE_IMAGE_EDIT, row.type)
        assertEquals("$imported\n$second", row.sourcePaths)
        f.vm.clearReferenceImages()
        assertTrue(f.vm.referenceImages.value.isEmpty())
        // Runs after the VM's scheduled deletion on the same dispatcher.
        kotlinx.coroutines.yield()
        assertFalse(File(imported).exists())
        assertFalse(File(second).exists())
        assertTrue(source.exists())
    }

    @Test
    fun `cancellation and failure keep the displayed preview as in 2_4_5 and allow retry`() = scenario {
        val f = fixture()
        f.vm.updatePrompt("preview")
        f.vm.generateImage()
        val cancelled = f.provider.requests.receive()
        cancelled.reply(partial = true)
        val preview = File(f.vm.currentGeneratedImages.first { it.isNotEmpty() }.single().filePath)
        f.vm.cancelGeneration()
        cancelled.finished.await()
        f.vm.isGenerating.first { !it }
        assertTrue(preview.exists())
        assertNull(f.vm.error.value)
        assertTrue(f.dao.getAllMedia().isEmpty())

        f.vm.generateImage()
        val failed = f.provider.requests.receive()
        assertTrue(f.vm.currentGeneratedImages.value.isEmpty())
        failed.reply(partial = true, index = 2)
        val failedPreview = File(f.vm.currentGeneratedImages.first { it.isNotEmpty() }.single().filePath)
        failed.responses.send(Result.failure(IllegalStateException("provider failure")))
        f.vm.isGenerating.first { !it }
        assertEquals("provider failure", f.vm.error.value)
        assertTrue(failedPreview.exists())

        f.vm.generateImage()
        val retry = f.provider.requests.receive()
        assertNull(f.vm.error.value)
        retry.reply()
        retry.responses.close()
        f.vm.isGenerating.first { !it }
        assertEquals(1, f.dao.getAllMedia().size)
    }

    @Test
    fun `gallery reads both legacy path forms and deletion removes database row before file`() = scenario {
        val f = fixture()
        for ((index, path) in listOf("images/legacy.png", "unprefixed.png").withIndex()) {
            val name = path.removePrefix("images/")
            File(f.images.apply { mkdirs() }, name).writeBytes(PNG)
            f.dao.insert(GenMediaEntity(path = path, prompt = name, modelId = "legacy", createAt = index.toLong()))
        }
        val gallery = f.gallery(f.vm)
        val loaded = gallery.first { it.size == 2 }
        assertEquals(listOf("unprefixed.png", "legacy.png"), loaded.map { File(it.filePath).name })
        loaded.forEach { assertTrue(File(it.filePath).exists()) }
        val victim = loaded.first()
        f.beforeDelete = { id ->
            assertEquals(victim.id, id)
            assertTrue(File(victim.filePath).exists())
        }
        f.vm.deleteImage(victim)
        gallery.first { it.size == 1 }
        assertFalse(File(victim.filePath).exists())
        assertEquals(loaded.last(), f.gallery(f.newVM()).first { it.isNotEmpty() }.single())
    }

    @Test
    fun `original input guards limits and model selection remain intact`() = scenario {
        val f = fixture()
        f.vm.generateImage()
        f.vm.updatePrompt("edit without references")
        f.vm.editImage()
        assertTrue(f.provider.requests.tryReceive().isFailure)
        f.vm.updateNumberOfImages(99)
        assertEquals(4, f.vm.numberOfImages.value)
        f.vm.updateNumberOfImages(0)
        assertEquals(1, f.vm.numberOfImages.value)
        f.vm.addReferenceImages(List(20) { "reference-$it" })
        assertEquals(16, f.vm.referenceImages.value.size)
        f.vm.updateImageGenerationModel(Uuid.random())
        f.store.settingsFlow.first { it.imageGenerationModelId != f.model.id }
        f.vm.generateImage()
        f.vm.error.first { it != null }
        assertEquals("No model selected", f.vm.error.value)
        assertFalse(f.vm.isGenerating.value)
        assertTrue(f.provider.requests.tryReceive().isFailure)
        f.vm.clearError()
        assertNull(f.vm.error.value)
        f.vm.startNewSession()
        assertEquals("", f.vm.prompt.value)
        assertTrue(f.vm.referenceImages.value.isEmpty())
    }

    private fun scenario(block: suspend CoroutineScope.() -> Unit) = runBlocking(ui) {
        withTimeout(15_000) { block() }
    }

    private suspend fun fixture() = Fixture().also {
        fixtures += it
        it.store.update { settings ->
            settings.copy(providers = listOf(it.providerSetting), imageGenerationModelId = it.model.id)
        }
    }

    private class Fixture {
        val root = Files.createTempDirectory("imggen-contract-").toFile()
        val images = File(root, "images")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = SettingsStore(object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                transform(data.value).also { data.value = it }
        }, scope)
        val database = buildAppDatabase(
            Room.inMemoryDatabaseBuilder<AppDatabase>(AppDatabaseConstructor::initialize),
            BundledSQLiteDriver(), MessageFtsDialect.UNICODE61,
        )
        var beforeDelete: (Int) -> Unit = {}
        val dao = object : GenMediaDAO by database.genMediaDao() {
            override suspend fun delete(id: Int) {
                beforeDelete(id)
                database.genMediaDao().delete(id)
            }
        }
        val model = Model(
            modelId = "contract-image", displayName = "Contract Model", type = ModelType.IMAGE,
            customHeaders = listOf(CustomHeader("X-Test", "original")),
            customBodies = listOf(CustomBody("quality", JsonPrimitive("low"))),
        )
        val providerSetting = ProviderSetting.OpenAI(models = listOf(model))
        val provider = ControlledProvider()
        val client = HttpClient(MockEngine { error("Unexpected HTTP request") })
        val manager = ProviderManager(client).apply { registerProvider("openai", provider) }
        val viewModels = ViewModelStore()
        val vm by lazy { newVM() }

        fun newVM() = ImgGenVM(
            store, manager, GenMediaRepository(dao), Path(root.path), Path(root.path, "cache"),
        ).also { viewModels.put(Uuid.random().toString(), it) }

        fun gallery(vm: ImgGenVM): MutableStateFlow<List<GeneratedImage>> {
            val rows = MutableStateFlow(emptyList<GeneratedImage>())
            val presenter = object : PagingDataPresenter<GeneratedImage>(Dispatchers.Main) {
                override suspend fun presentPagingDataEvent(event: PagingDataEvent<GeneratedImage>) {
                    rows.value = snapshot().items
                }
            }
            scope.launch { vm.generatedImages.collectLatest(presenter::collectFrom) }
            return rows
        }

        fun close() {
            viewModels.clear()
            scope.cancel()
            database.close()
            client.close()
            root.deleteRecursively()
        }
    }

    private class Call(
        val setting: ProviderSetting,
        val generation: ImageGenerationParams? = null,
        val edit: ImageEditParams? = null,
    ) {
        val responses = Channel<Result<ImageGenerationItem>>(Channel.UNLIMITED)
        val finished = CompletableDeferred<Unit>()
        suspend fun reply(partial: Boolean = false, index: Int? = null, dataUri: Boolean = false) {
            responses.send(Result.success(ImageGenerationItem(
                data = (if (dataUri) "data:image/png;base64," else "") + Base64.encode(PNG),
                mimeType = "image/png", partial = partial, partialImageIndex = index,
            )))
        }
    }

    private class ControlledProvider : Provider<ProviderSetting.OpenAI> {
        val requests = Channel<Call>(Channel.UNLIMITED)
        override suspend fun generateImage(providerSetting: ProviderSetting, params: ImageGenerationParams) =
            responses(Call(providerSetting, generation = params))
        override suspend fun editImage(providerSetting: ProviderSetting, params: ImageEditParams) =
            responses(Call(providerSetting, edit = params))
        private fun responses(call: Call): Flow<ImageGenerationItem> = flow {
            requests.send(call)
            try {
                for (result in call.responses) emit(result.getOrThrow())
            } finally {
                call.finished.complete(Unit)
            }
        }
        override suspend fun listModels(providerSetting: ProviderSetting.OpenAI): List<Model> = emptyList()
        override suspend fun generateText(
            providerSetting: ProviderSetting.OpenAI, messages: List<UIMessage>, params: TextGenerationParams,
        ): MessageChunk = error("Unused")
        override suspend fun streamText(
            providerSetting: ProviderSetting.OpenAI, messages: List<UIMessage>, params: TextGenerationParams,
        ): Flow<MessageChunk> = error("Unused")
    }

    companion object {
        private val PNG = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=",
        )
    }
}
