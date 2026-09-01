package me.rerere.rikkahub.data.files

data class SkillSummary(
    /** frontmatter 里的显示名，可能与所在目录名不同 */
    val name: String,
    /** 技能所在目录名，文件读写一律以它为准 */
    val directoryName: String,
    val description: String,
    val compatibility: String? = null,
    val allowedTools: List<String> = emptyList(),
)

data class StoredSkillFile(
    val name: String,
    val relativePath: String,
    val size: Long,
    val isDirectory: Boolean,
)

interface SkillStore {
    suspend fun listSkills(): List<SkillSummary>

    suspend fun saveSkill(name: String, content: String): Boolean

    suspend fun saveSkillFiles(name: String, files: Map<String, String>): Boolean

    suspend fun saveSkillFileBytes(name: String, files: Map<String, ByteArray>): Boolean

    suspend fun deleteSkill(name: String): Boolean

    suspend fun listSkillFiles(name: String): List<StoredSkillFile>

    suspend fun readSkillFile(name: String, relativePath: String): String?

    suspend fun saveSkillFile(name: String, relativePath: String, content: String): Boolean

    suspend fun deleteSkillFile(name: String, relativePath: String): Boolean
}
