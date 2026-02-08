package me.rerere.rikkahub

import android.app.Application
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.utils.DatabaseUtil
import org.jetbrains.compose.resources.getString
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import rikkahub.composeapp.generated.resources.*

private const val TAG = "RikkaHubApp"
class RikkaHubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppInitializer.initKoin {
            androidLogger()
            androidContext(this@RikkaHubApp)
            workManagerFactory()
        }
        this.createNotificationChannel()

        // set cursor window size to 32MB
        DatabaseUtil.setCursorWindowSize(32 * 1024 * 1024)

        AppInitializer.initialize()
    }

    private fun createNotificationChannel() {
        val notificationManager = NotificationManagerCompat.from(this)
        val chatCompletedChannel = NotificationChannelCompat
            .Builder(
                CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH
            )
            .setName(runBlocking { getString(Res.string.notification_channel_chat_completed) })
            .setVibrationEnabled(true)
            .build()
        notificationManager.createNotificationChannel(chatCompletedChannel)

        val chatLiveUpdateChannel = NotificationChannelCompat
            .Builder(
                CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_LOW
            )
            .setName(runBlocking { getString(Res.string.notification_channel_chat_live_update) })
            .setVibrationEnabled(false)
            .build()
        notificationManager.createNotificationChannel(chatLiveUpdateChannel)
    }

    override fun onTerminate() {
        super.onTerminate()
        get<AppScope>().cancel()
    }
}
