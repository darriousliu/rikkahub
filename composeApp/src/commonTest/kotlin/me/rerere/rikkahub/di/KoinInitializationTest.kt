package me.rerere.rikkahub.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.platform.ChatLiveUpdateNotification
import me.rerere.rikkahub.platform.ChatNotificationPresenter
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class KoinInitializationTest {
    @Test
    fun applicationConfigurationRunsBeforeEagerCreationAndSharesGlobalInstances() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val preferences = object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                transform(data.value).also { data.value = it }
        }
        val settings = SettingsStore(preferences, backgroundScope)
        val platformJson = Json { ignoreUnknownKeys = true }
        try {
            val application = initKoin {
                modules(module {
                    single { settings }
                    single<ChatNotificationPresenter> {
                        object : ChatNotificationPresenter {
                            override fun showLiveUpdate(notification: ChatLiveUpdateNotification) {}
                            override fun showGenerationCompleted(
                                conversationId: Uuid,
                                senderName: String,
                                contentPreview: String,
                            ) {}
                            override fun cancelLiveUpdate(conversationId: Uuid) {}
                        }
                    }
                    single<Json> { platformJson }
                    single(createdAtStart = true) { StartupProbe(get()) }
                })
            }
            assertSame(application.koin, KoinPlatform.getKoin())
            assertSame(platformJson, application.koin.get<StartupProbe>().json)
            assertSame(application.koin.get<AppEventBus>(), Consumer().events)

            val scope = application.koin.get<AppScope>()
            assertSame(scope, application.koin.get<CoroutineScope>())
            assertTrue(scope.coroutineContext.job.isActive)
            val child = scope.launch { error("Closed application work must not run") }
            stopKoin()
            assertTrue(scope.coroutineContext.job.isCancelled)
            assertTrue(child.isCancelled)
        } finally {
            stopKoin()
            Dispatchers.resetMain()
        }
    }

    private class StartupProbe(val json: Json)

    private class Consumer : KoinComponent {
        val events by inject<AppEventBus>()
    }
}
