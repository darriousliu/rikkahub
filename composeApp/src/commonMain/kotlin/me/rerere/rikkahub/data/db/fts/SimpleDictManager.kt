package me.rerere.rikkahub.data.db.fts

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.toKotlinxIoPath
import kotlinx.io.files.Path
import me.rerere.rikkahub.generated.resources.Res
import me.rerere.rikkahub.utils.deleteRecursively
import me.rerere.rikkahub.utils.exists
import me.rerere.rikkahub.utils.mkdirs
import me.rerere.rikkahub.utils.readText
import me.rerere.rikkahub.utils.writeBytes
import me.rerere.rikkahub.utils.writeText
import org.jetbrains.compose.resources.ExperimentalResourceApi

object SimpleDictManager {
    private const val DICT_ASSET_DIR = "simple_dict"
    private const val VERSION_FILE = "version.txt"

    // 与资源中的词典版本对齐，更新词典时递增此值。
    private const val CURRENT_VERSION = 1
    private val dictionaryFiles = listOf(
        "jieba.dict.utf8", "hmm_model.utf8", "user.dict.utf8", "idf.utf8", "stop_words.utf8",
        "pos_dict/char_state_tab.utf8", "pos_dict/prob_emit.utf8",
        "pos_dict/prob_start.utf8", "pos_dict/prob_trans.utf8",
    )

    /** 将共享词典解压到 files/simple_dict；已是最新版本时无需重复拷贝。 */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun extractDict(): Path {
        val destDir = Path(FileKit.filesDir.toKotlinxIoPath(), DICT_ASSET_DIR)
        val versionFile = Path(destDir, VERSION_FILE)
        if (versionFile.exists() && versionFile.readText().trim().toIntOrNull() == CURRENT_VERSION) {
            return destDir
        }

        destDir.deleteRecursively()
        destDir.mkdirs()
        dictionaryFiles.forEach { name ->
            val file = Path(destDir, name)
            file.parent?.mkdirs()
            file.writeBytes(Res.readBytes("files/$DICT_ASSET_DIR/$name"))
        }
        versionFile.writeText(CURRENT_VERSION.toString())
        return destDir
    }
}
