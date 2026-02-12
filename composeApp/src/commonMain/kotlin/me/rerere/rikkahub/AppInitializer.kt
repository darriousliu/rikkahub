package me.rerere.rikkahub

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.remoteconfig.FirebaseRemoteConfig
import io.github.vinceglb.filekit.exists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import me.rerere.common.PlatformContext
import me.rerere.common.android.appTempFolder
import me.rerere.common.utils.deleteRecursively
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.di.appModule
import me.rerere.rikkahub.di.dataSourceModule
import me.rerere.rikkahub.di.platformModule
import me.rerere.rikkahub.di.repositoryModule
import me.rerere.rikkahub.di.viewModelModule
import me.rerere.rikkahub.ui.components.richtext.initMermaidRenderer
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import kotlin.time.Duration.Companion.minutes

object AppInitializer : KoinComponent {
    private val remoteConfigDefaults = arrayOf(
        "silicon_cloud_api_key" to "c2stem9ycWpnZmhlcm9ycG1vYXJnZnJocGFxd3ViemJtdnN3cWxtc3pvbnp5bXFqY2xv",
        "silicon_cloud_free_models" to "Qwen/Qwen3-8B,THUDM/GLM-4.1V-9B-Thinking"
    )

    fun initKoin(application: KoinAppDeclaration) {
        startKoin {
            application()
            modules(platformModule, appModule, viewModelModule, dataSourceModule, repositoryModule)
        }
    }

    fun initialize() {
        // delete temp files
        deleteTempFiles()

        // sync upload files to DB
        syncManagedFiles()

        // Init remote config
        get<AppScope>().launch(Dispatchers.IO) {
            get<FirebaseRemoteConfig>().apply {
                settings {
                    minimumFetchInterval = 30.minutes
                }

                setDefaults(*remoteConfigDefaults)
                fetchAndActivate()
            }
        }
        get<AppScope>().launch {
            initMermaidRenderer()
        }
    }

    private fun deleteTempFiles() {
        get<AppScope>().launch(Dispatchers.IO) {
            val dir = get<PlatformContext>().appTempFolder
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        }
    }

    private fun syncManagedFiles() {
        get<AppScope>().launch(Dispatchers.IO) {
            runCatching {
                get<FilesManager>().syncFolder()
            }.onFailure {
                Logger.e("FilesManager", it){ "syncManagedFiles failed" }
            }
        }
    }
}
