package me.rerere.rikkahub.data.files

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidSkillStore(
    private val skillManager: SkillManager,
) : SkillStore {
    override suspend fun listSkills(): List<SkillSummary> = withContext(Dispatchers.IO) {
        skillManager.listSkills().map { skill ->
            SkillSummary(
                name = skill.name,
                description = skill.description,
                compatibility = skill.compatibility,
                allowedTools = skill.allowedTools,
            )
        }
    }

    override suspend fun saveSkill(name: String, content: String): Boolean = withContext(Dispatchers.IO) {
        skillManager.saveSkill(name, content) != null
    }

    override suspend fun saveSkillFiles(name: String, files: Map<String, String>): Boolean =
        withContext(Dispatchers.IO) {
            skillManager.saveSkillFilesAtomically(name, files)
        }

    override suspend fun saveSkillFileBytes(name: String, files: Map<String, ByteArray>): Boolean =
        withContext(Dispatchers.IO) {
            skillManager.saveSkillFileBytesAtomically(name, files)
        }

    override suspend fun deleteSkill(name: String): Boolean = skillManager.deleteSkill(name)

    override suspend fun listSkillFiles(name: String): List<StoredSkillFile> = withContext(Dispatchers.IO) {
        val root = skillManager.getSkillDir(name) ?: return@withContext emptyList()
        buildList { appendFiles(root, root) }
    }

    override suspend fun readSkillFile(name: String, relativePath: String): String? = withContext(Dispatchers.IO) {
        skillManager.resolveSkillFile(name, relativePath)
            ?.takeIf(File::isFile)
            ?.readText()
    }

    override suspend fun saveSkillFile(name: String, relativePath: String, content: String): Boolean =
        withContext(Dispatchers.IO) {
            skillManager.saveSkillFile(name, relativePath, content)
        }

    override suspend fun deleteSkillFile(name: String, relativePath: String): Boolean =
        withContext(Dispatchers.IO) {
            skillManager.deleteSkillFile(name, relativePath)
        }

    private fun MutableList<StoredSkillFile>.appendFiles(root: File, directory: File) {
        directory.listFiles()?.forEach { file ->
            add(
                StoredSkillFile(
                    name = file.name,
                    relativePath = file.relativeTo(root).invariantSeparatorsPath,
                    size = if (file.isFile) file.length() else 0L,
                    isDirectory = file.isDirectory,
                )
            )
            if (file.isDirectory) appendFiles(root, file)
        }
    }
}
