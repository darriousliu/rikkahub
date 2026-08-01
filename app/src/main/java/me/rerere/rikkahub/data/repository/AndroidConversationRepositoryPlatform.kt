package me.rerere.rikkahub.data.repository

import android.database.sqlite.SQLiteBlobTooBigException
import androidx.core.net.toUri
import me.rerere.rikkahub.data.files.FilesManager

class AndroidConversationFileStore(
    private val filesManager: FilesManager,
) : ConversationFileStore {
    override fun deleteChatFiles(urls: List<String>) {
        filesManager.deleteChatFiles(urls.map { it.toUri() })
    }
}

object AndroidMessageNodeReadErrorPolicy : MessageNodeReadErrorPolicy {
    override fun canSkip(error: Throwable): Boolean {
        return error is SQLiteBlobTooBigException || error is IllegalStateException
    }
}
