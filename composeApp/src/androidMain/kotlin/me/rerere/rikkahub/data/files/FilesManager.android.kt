package me.rerere.rikkahub.data.files

import android.net.Uri
import androidx.core.net.toUri
import io.github.vinceglb.filekit.PlatformFile
import me.rerere.rikkahub.data.db.entity.ManagedFileEntity

fun FilesManager.createChatFilesByContents(uris: List<Uri>): List<Uri> =
    createChatFilesByContents(uris.map(::PlatformFile)).map(String::toUri)

suspend fun FilesManager.saveUploadFromUri(
    uri: Uri,
    displayName: String? = null,
    mimeType: String? = null,
): ManagedFileEntity = saveUploadFromUri(PlatformFile(uri), displayName, mimeType)
