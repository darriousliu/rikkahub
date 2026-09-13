package me.rerere.rikkahub.di

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CoroutineScope
import me.rerere.rikkahub.data.datastore.createJvmSettingsDataStore
import me.rerere.rikkahub.data.db.createJvmAppDatabase
import me.rerere.rikkahub.data.db.defaultJvmDatabaseFile
import me.rerere.rikkahub.data.sync.BackupFileLayout
import me.rerere.rikkahub.platform.AnalyticsTracker
import me.rerere.rikkahub.platform.ChatNotificationPresenter
import me.rerere.rikkahub.platform.CrashReporter
import me.rerere.rikkahub.platform.ExternalUriOpener
import me.rerere.rikkahub.platform.JvmExternalUriOpener
import me.rerere.rikkahub.platform.JvmOAuthCallbackSessionFactory
import me.rerere.rikkahub.platform.JvmSentryMonitoring
import me.rerere.rikkahub.platform.JvmSystemTrayChatNotificationPresenter
import me.rerere.rikkahub.platform.OAuthCallbackSessionFactory
import me.rerere.rikkahub.shared.currentDesktopPlatformBuildInfo
import me.rerere.rikkahub.web.WebServerRuntime
import me.rerere.rikkahub.web.createJvmWebServerRuntime
import me.rerere.tts.controller.JvmAudioPlayer
import me.rerere.tts.controller.PlatformAudioPlayer
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.provider.providers.JvmSystemTTSProvider
import org.koin.dsl.module

fun createJvmAppModule(appScope: CoroutineScope) = module {
    includes(createAppModule(appScope))
    single { createJvmSettingsDataStore(appScope) }
    single { createJvmAppDatabase(defaultJvmDatabaseFile()) }
    single { currentDesktopPlatformBuildInfo() }
    single { BackupFileLayout.create(PlatformFile(defaultJvmDatabaseFile())) }
    single<ExternalUriOpener> { JvmExternalUriOpener() }
    single<OAuthCallbackSessionFactory> { JvmOAuthCallbackSessionFactory(get()) }
    single<WebServerRuntime> { createJvmWebServerRuntime(appScope) }
    single { JvmSentryMonitoring() }
    single<AnalyticsTracker> { get<JvmSentryMonitoring>() }
    single<CrashReporter> { get<JvmSentryMonitoring>() }
    single<ChatNotificationPresenter> { JvmSystemTrayChatNotificationPresenter() }
    single<TTSProvider<TTSProviderSetting.SystemTTS>> { JvmSystemTTSProvider() }
    single<PlatformAudioPlayer> { JvmAudioPlayer() }
}
