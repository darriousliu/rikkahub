package me.rerere.rikkahub

import dev.gitlive.firebase.remoteconfig.FirebaseRemoteConfig
import io.github.vinceglb.filekit.exists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import me.rerere.common.PlatformContext
import me.rerere.common.android.appTempFolder
import me.rerere.common.utils.delete
import me.rerere.rikkahub.di.appModule
import me.rerere.rikkahub.di.dataSourceModule
import me.rerere.rikkahub.di.platformModule
import me.rerere.rikkahub.di.repositoryModule
import me.rerere.rikkahub.di.viewModelModule
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
    }

    private fun deleteTempFiles() {
        get<AppScope>().launch(Dispatchers.IO) {
            val dir = get<PlatformContext>().appTempFolder
            if (dir.exists()) {
                dir.delete()
            }
        }
    }
}
