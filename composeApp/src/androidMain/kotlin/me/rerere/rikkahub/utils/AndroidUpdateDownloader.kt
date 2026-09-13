package me.rerere.rikkahub.utils

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.core.net.toUri

fun UpdateChecker.downloadUpdate(context: Context, download: UpdateDownload) {
    runCatching {
        val request = DownloadManager.Request(download.url.toUri()).apply {
            setTitle(download.name)
            setDescription("正在下载更新包...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, download.name)
            setMimeType("application/vnd.android.package-archive")
        }
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
    }.onFailure {
        Toast.makeText(context, "Failed to update", Toast.LENGTH_SHORT).show()
        context.openUrl(download.url)
    }
}
