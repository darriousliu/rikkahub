package me.rerere.rikkahub.data.files

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.atomicMove
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.isDirectory
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.list
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.readString
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.write
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.CancellationException
import me.rerere.rikkahub.data.datastore.SettingsStore
import kotlin.uuid.Uuid

class FileKitSkillStore(
    private val settingsStore: SettingsStore,
    private val root: PlatformFile = FileKit.filesDir / SKILLS_DIRECTORY,
) : SkillStore {
    override suspend fun listSkills(): List<SkillSummary> {
        ensureRoot()
        return buildList {
            root.list().forEach { directory ->
                if (directory.isDirectory()) {
                    parseSkill(directory / SKILL_FILE_NAME)?.let(::add)
                }
            }
        }.sortedBy(SkillSummary::name)
    }

    override suspend fun saveSkill(name: String, content: String): Boolean =
        saveSkillFileBytes(name, mapOf(SKILL_FILE_NAME to content.encodeToByteArray()))

    override suspend fun saveSkillFiles(name: String, files: Map<String, String>): Boolean =
        saveSkillFileBytes(name, files.mapValues { (_, content) -> content.encodeToByteArray() })

    override suspend fun saveSkillFileBytes(name: String, files: Map<String, ByteArray>): Boolean {
        val skillName = name.validPathSegment() ?: return false
        val validatedFiles = files.mapKeys { (path, _) -> path.validRelativePath() ?: return false }
        if (SKILL_FILE_NAME !in validatedFiles) return false

        ensureRoot()
        val target = root / skillName
        val operationId = Uuid.random().toString()
        val staging = root / ".$skillName.staging.$operationId"
        val backup = root / ".$skillName.backup.$operationId"
        var movedExisting = false
        try {
            staging.createDirectories(mustCreate = true)
            validatedFiles.forEach { (relativePath, bytes) ->
                val destination = staging.resolveValidated(relativePath)
                destination.parent()?.createDirectories()
                destination.write(bytes)
            }

            if (target.exists()) {
                target.atomicMove(backup)
                movedExisting = true
            }
            staging.atomicMove(target)
            if (movedExisting) backup.deleteRecursively()
            return true
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (movedExisting && !target.exists() && backup.exists()) {
                runCatching { backup.atomicMove(target) }
            }
            return false
        } finally {
            runCatching { staging.deleteRecursively() }
            if (target.exists()) runCatching { backup.deleteRecursively() }
        }
    }

    override suspend fun deleteSkill(name: String): Boolean {
        val skillName = name.validPathSegment() ?: return false
        val target = root / skillName
        if (!target.exists()) return false
        target.deleteRecursively()
        settingsStore.update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (skillName in assistant.enabledSkills) {
                        assistant.copy(enabledSkills = assistant.enabledSkills - skillName)
                    } else {
                        assistant
                    }
                },
            )
        }
        return true
    }

    override suspend fun listSkillFiles(name: String): List<StoredSkillFile> {
        val skillName = name.validPathSegment() ?: return emptyList()
        val directory = root / skillName
        if (!directory.isDirectory()) return emptyList()
        return buildList { appendFiles(directory, relativeDirectory = "") }
            .sortedBy(StoredSkillFile::relativePath)
    }

    override suspend fun readSkillFile(name: String, relativePath: String): String? {
        val file = resolveSkillFile(name, relativePath) ?: return null
        return file.takeIf(PlatformFile::isRegularFile)?.readString()
    }

    override suspend fun saveSkillFile(name: String, relativePath: String, content: String): Boolean {
        val file = resolveSkillFile(name, relativePath) ?: return false
        return try {
            file.parent()?.createDirectories()
            file.writeString(content)
            true
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            false
        }
    }

    override suspend fun deleteSkillFile(name: String, relativePath: String): Boolean {
        val file = resolveSkillFile(name, relativePath) ?: return false
        if (!file.exists()) return false
        return try {
            file.delete()
            true
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            false
        }
    }

    private fun ensureRoot() {
        root.createDirectories()
    }

    private suspend fun parseSkill(skillFile: PlatformFile): SkillSummary? {
        if (!skillFile.isRegularFile()) return null
        return runCatching {
            val frontmatter = SkillFrontmatterParser.parse(skillFile.readString())
            val name = frontmatter["name"]?.takeIf(String::isNotBlank) ?: return null
            val description = frontmatter["description"]?.takeIf(String::isNotBlank) ?: return null
            SkillSummary(
                name = name,
                description = description,
                compatibility = frontmatter["compatibility"],
                allowedTools = frontmatter["allowed-tools"]
                    ?.split(' ')
                    ?.filter(String::isNotBlank)
                    .orEmpty(),
            )
        }.getOrNull()
    }

    private fun resolveSkillFile(name: String, relativePath: String): PlatformFile? {
        val skillName = name.validPathSegment() ?: return null
        val safeRelativePath = relativePath.validRelativePath() ?: return null
        return (root / skillName).resolveValidated(safeRelativePath)
    }

    private fun PlatformFile.resolveValidated(relativePath: String): PlatformFile =
        relativePath.split('/').fold(this) { parent, segment -> parent / segment }

    private fun MutableList<StoredSkillFile>.appendFiles(
        directory: PlatformFile,
        relativeDirectory: String,
    ) {
        directory.list().forEach { file ->
            val relativePath = listOf(relativeDirectory, file.name)
                .filter(String::isNotBlank)
                .joinToString("/")
            val isDirectory = file.isDirectory()
            add(
                StoredSkillFile(
                    name = file.name,
                    relativePath = relativePath,
                    size = if (isDirectory) 0L else file.size(),
                    isDirectory = isDirectory,
                ),
            )
            if (isDirectory) appendFiles(file, relativePath)
        }
    }
}

private suspend fun PlatformFile.deleteRecursively() {
    if (!exists()) return
    if (isDirectory()) list().forEach { it.deleteRecursively() }
    delete(mustExist = false)
}

private fun String.validPathSegment(): String? =
    trim()
        .takeIf(String::isNotEmpty)
        ?.takeIf { value ->
            value != "." &&
                value != ".." &&
                value.none { character ->
                    character == '/' || character == '\\' || character.isISOControl()
                }
        }

private fun String.validRelativePath(): String? {
    val normalized = replace('\\', '/')
    if (normalized.startsWith('/')) return null
    val segments = normalized.split('/')
    if (segments.isEmpty() || segments.any { it.isEmpty() || it == "." || it == ".." }) return null
    if (segments.any { segment -> segment.any(Char::isISOControl) }) return null
    return segments.joinToString("/")
}

private const val SKILLS_DIRECTORY = "skills"
private const val SKILL_FILE_NAME = "SKILL.md"
