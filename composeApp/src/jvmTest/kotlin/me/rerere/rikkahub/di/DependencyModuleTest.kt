package me.rerere.rikkahub.di

import androidx.lifecycle.ViewModelStore
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.api.SponsorAPI
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.model.Sponsor
import me.rerere.rikkahub.generated.resources.Res
import me.rerere.rikkahub.service.ChatServiceTestFixture
import me.rerere.rikkahub.shared.PlatformBuildInfo
import me.rerere.rikkahub.ui.pages.imggen.ImgGenVM
import me.rerere.rikkahub.ui.theme.ChatFontRuntime
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.utils.UpdateChecker
import me.rerere.rikkahub.utils.UpdateInfo
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class DependencyModuleTest {
    @Test
    @OptIn(ExperimentalResourceApi::class)
    fun commonServicesUseFileKitDirectoriesWithoutPathRegistrations() = runTest {
        val root = Files.createTempDirectory("filekit-di-").toFile()
        val filesDir = root.resolve("files")
        val cacheDir = root.resolve("cache")
        val viewModels = ViewModelStore()
        FileKit.init(filesDir = filesDir, cacheDir = cacheDir)
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            ChatServiceTestFixture().use { fixture ->
                val application = koinApplication(createEagerInstances = false) {
                    modules(createAppModule(fixture.scope), module {
                        single { fixture.settings }
                        single { fixture.database }
                        single { fixture.providers }
                    })
                }
                try {
                    lateinit var files: FilesManager
                    fixture.awaitLaunched { files = application.koin.get() }
                    val bytes = "FileKit 中文".encodeToByteArray()
                    val uploaded = files.saveManagedFromBytes(FileFolders.UPLOAD, bytes, "note.txt")
                    assertContentEquals(bytes, filesDir.resolve(uploaded.relativePath).readBytes())
                    assertEquals(uploaded, files.get(uploaded.id))

                    val skills = application.koin.get<SkillManager>()
                    val content = "---\nname: filekit-test\ndescription: Directory test\n---\nBody"
                    assertNotNull(skills.saveSkill("filekit-test", content))
                    assertEquals(content, filesDir.resolve("skills/filekit-test/SKILL.md").readText())

                    val font = application.koin.get<ChatFontRuntime>()
                    val source = root.resolve("font.ttf").apply { writeBytes(Res.readBytes("font/jetbrains_mono.ttf")) }
                    val imported = font.import(PlatformFile(source)).getOrThrow()
                    assertContentEquals(source.readBytes(), filesDir.resolve(imported.relativePath).readBytes())
                    assertNotNull(font.load(imported.relativePath))
                    application.koin.get<GenerationHandler>()

                    val vm = application.koin.get<ImgGenVM>().also { viewModels.put("imggen", it) }
                    val image = root.resolve("source.png")
                    ImageIO.write(BufferedImage(8, 4, BufferedImage.TYPE_INT_RGB), "png", image)
                    vm.importReferenceImages(listOf(PlatformFile(image)))
                    val reference = File(vm.referenceImages.first { it.isNotEmpty() }.single())
                    assertEquals(cacheDir.resolve("temp").canonicalFile, reference.parentFile.canonicalFile)
                    assertEquals(8, ImageIO.read(reference).width)
                } finally {
                    viewModels.clear()
                    application.close()
                }
            }
        } finally {
            Dispatchers.resetMain()
            FileKit.init("RikkaHub")
            root.deleteRecursively()
        }
    }

    @Test
    fun commonRegistrationsUseTheSuppliedClientAndBuildInfo() = runTest {
        val requests = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            requests += request.url.toString()
            val body = when (request.url.host) {
                "updates.rikka-ai.com" -> {
                    assertEquals("RikkaHub 2.4.5 #245", request.headers[HttpHeaders.UserAgent])
                    """{"version":"2.4.5","publishedAt":"2026-09-13","changelog":"CMP50","downloads":[]}"""
                }
                "sponsors.rikka-ai.com" -> """[{"userName":"CMP50","avatar":"avatar","amount":"1"}]"""
                else -> error("Unexpected request: ${request.url}")
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val application = koinApplication {
            modules(appModule, dataSourceModule, module {
                single { client }
                single { PlatformBuildInfo("2.4.5", "245", true, "test", "test") }
            })
        }
        try {
            val checker = application.koin.get<UpdateChecker>()
            val sponsors = application.koin.get<SponsorAPI>()
            assertSame(checker, application.koin.get<UpdateChecker>())
            assertSame(sponsors, application.koin.get<SponsorAPI>())
            val states = checker.checkUpdate().toList()
            assertEquals(2, states.size)
            assertIs<UiState.Loading>(states.first())
            assertEquals("CMP50", assertIs<UiState.Success<UpdateInfo>>(states.last()).data.changelog)
            assertEquals(listOf(Sponsor("CMP50", "avatar", "1")), sponsors.getSponsors())
            assertEquals(listOf("https://updates.rikka-ai.com/", "https://sponsors.rikka-ai.com/sponsors"), requests)
        } finally {
            application.close()
            client.close()
        }
    }
}
