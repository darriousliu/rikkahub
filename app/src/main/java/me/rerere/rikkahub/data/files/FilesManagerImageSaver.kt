package me.rerere.rikkahub.data.files

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.net.toFile
import androidx.core.net.toUri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.common.logging.RikkaLog as Log
import me.rerere.rikkahub.utils.exportImage
import me.rerere.rikkahub.utils.exportImageFile
import me.rerere.rikkahub.utils.getActivity
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
suspend fun FilesManager.saveMessageImage(activityContext: Context, image: String) = withContext(Dispatchers.IO) {
    val activity = requireNotNull(activityContext.getActivity()) { "Activity not found" }
    when {
        image.startsWith("data:image") -> {
            val byteArray = Base64.decode(image.substringAfter("base64,").toByteArray())
            val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
            activityContext.exportImage(activity, bitmap)
        }

        image.startsWith("file:") -> {
            val file = image.toUri().toFile()
            activityContext.exportImageFile(activity, file)
        }

        image.startsWith("/") -> {
            activityContext.exportImageFile(activity, File(image))
        }

        image.startsWith("http") -> {
            runCatching {
                val url = URL(image)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val bitmap = BitmapFactory.decodeStream(connection.inputStream)
                    activityContext.exportImage(activity, bitmap)
                } else {
                    Log.e(
                        "FilesManager",
                        "saveMessageImage: Failed to download image from $image, response code: ${connection.responseCode}"
                    )
                }
            }.getOrNull()
        }

        else -> error("Invalid image format")
    }
}
