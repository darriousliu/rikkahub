package me.rerere.rikkahub.utils

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.core.net.toUri
import me.rerere.common.PlatformContext

actual fun platformDownloadUpdate(context: PlatformContext, download: UpdateDownload) {
    runCatching {
        val request = DownloadManager.Request(download.url.toUri()).apply {
            // 设置下载时通知栏的标题和描述
            setTitle(download.name)
            setDescription("正在下载更新包...")
            // 下载完成后通知栏可见
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            // 允许在移动网络和WiFi下下载
            setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            // 设置文件保存路径
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, download.name)
            // 允许下载的文件类型
            setMimeType("application/vnd.android.package-archive")
        }
        // 获取系统的DownloadManager
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        // 你可以保存返回的downloadId到本地，以便后续查询下载进度或状态
    }.onFailure {
        Toast.makeText(context, "Failed to update", Toast.LENGTH_SHORT).show()
        context.openUrl(download.url) // 跳转到下载页面
    }
}

