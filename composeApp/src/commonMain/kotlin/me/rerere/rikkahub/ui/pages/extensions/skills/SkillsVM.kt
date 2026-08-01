package me.rerere.rikkahub.ui.pages.extensions.skills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.common.archive.PlatformZipArchive
import me.rerere.common.archive.ZipEntryPathPolicy
import me.rerere.common.archive.readBytes
import me.rerere.rikkahub.data.files.SkillFrontmatterParser
import me.rerere.rikkahub.data.files.SkillStore
import me.rerere.rikkahub.data.files.SkillSummary

class SkillsVM(
    private val skillStore: SkillStore,
    private val httpClient: HttpClient,
) : ViewModel() {
    private val _skills = MutableStateFlow<List<SkillSummary>>(emptyList())
    val skills = _skills.asStateFlow()

    init {
        loadSkills()
    }

    private fun loadSkills() {
        viewModelScope.launch {
            _skills.value = skillStore.listSkills()
        }
    }

    fun saveSkill(name: String, content: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = skillStore.saveSkill(name, content)
            _skills.value = skillStore.listSkills()
            onResult(success)
        }
    }

    fun deleteSkill(name: String) {
        viewModelScope.launch {
            skillStore.deleteSkill(name)
            _skills.value = skillStore.listSkills()
        }
    }

    fun importSkillFromFile(file: PlatformFile, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val bytes = file.readBytes()
                if (isZipFile(file.name, bytes)) {
                    importSkillsFromZip(bytes)
                } else {
                    importSkillMarkdown(bytes)
                }
            }.onSuccess { importedNames ->
                _skills.value = skillStore.listSkills()
                onResult(true, importedNames.joinToString())
            }.onFailure { error ->
                onResult(false, error.message ?: "未知错误")
            }
        }
    }

    fun importSkillFromGitHub(repoUrl: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val info = parseGitHubUrl(repoUrl) ?: error("无效的 GitHub 仓库链接")
                val files = mutableListOf<Pair<String, String>>()
                check(listFilesRecursively(info.owner, info.repo, info.branch, info.path, info.path, files)) {
                    "读取 GitHub 目录失败"
                }

                val skillMdEntry = files.find { it.first == "SKILL.md" }
                    ?: error("目录中未找到 SKILL.md")
                val skillMdContent = downloadText(skillMdEntry.second)
                    ?: error("下载 SKILL.md 失败，请检查链接或网络")
                val name = SkillFrontmatterParser.parse(skillMdContent)["name"]
                    ?.takeIf { it.isNotBlank() }
                    ?: error("SKILL.md 格式错误：缺少 name 字段")

                val fileContents = LinkedHashMap<String, String>()
                for ((relativePath, downloadUrl) in files) {
                    fileContents[relativePath] = downloadText(downloadUrl)
                        ?: error("下载文件失败：$relativePath")
                }
                check(skillStore.saveSkillFiles(name, fileContents)) { "保存失败" }
                name
            }.onSuccess { name ->
                _skills.value = skillStore.listSkills()
                onResult(true, name)
            }.onFailure { error ->
                onResult(false, error.message ?: "未知错误")
            }
        }
    }

    private suspend fun importSkillMarkdown(bytes: ByteArray): List<String> {
        val content = bytes.decodeToString()
        val frontmatter = SkillFrontmatterParser.parse(content)
        val name = frontmatter["name"]?.trim()
        if (name.isNullOrBlank()) error("SKILL.md 格式错误：缺少 name 字段")
        if (frontmatter["description"].isNullOrBlank()) {
            error("SKILL.md 格式错误：缺少 description 字段")
        }
        check(skillStore.saveSkill(name, content)) { "保存失败，请检查技能格式" }
        return listOf(name)
    }

    private suspend fun importSkillsFromZip(bytes: ByteArray): List<String> {
        val files = LinkedHashMap<String, ByteArray>()
        PlatformZipArchive.read(Buffer().apply { write(bytes) }) { entry ->
            val path = ZipEntryPathPolicy.normalizeOrNull(entry.name)
                ?: error("压缩包包含不安全的文件路径")
            if (!entry.isDirectory) files[path] = entry.readBytes()
        }

        val skillMdPaths = files.keys
            .filter { it.substringAfterLast('/').equals("SKILL.md", ignoreCase = true) }
            .sorted()
        if (skillMdPaths.isEmpty()) error("压缩包中未找到 SKILL.md")
        val skillBasePaths = skillMdPaths.map { it.substringBeforeLast('/', missingDelimiterValue = "") }

        val importedNames = mutableListOf<String>()
        for (skillMdPath in skillMdPaths) {
            val skillContent = files[skillMdPath]?.decodeToString() ?: error("读取失败：$skillMdPath")
            val frontmatter = SkillFrontmatterParser.parse(skillContent)
            val name = frontmatter["name"]?.trim()
            if (name.isNullOrBlank()) error("$skillMdPath 格式错误：缺少 name 字段")
            if (frontmatter["description"].isNullOrBlank()) {
                error("$skillMdPath 格式错误：缺少 description 字段")
            }

            val basePath = skillMdPath.substringBeforeLast('/', missingDelimiterValue = "")
            val skillFiles = LinkedHashMap<String, ByteArray>()
            for ((path, content) in files) {
                if (isInsideNestedSkill(path, basePath, skillBasePaths)) continue
                val relativePath = relativeToSkillBase(path, basePath) ?: continue
                val targetPath = if (relativePath.equals("SKILL.md", ignoreCase = true)) {
                    "SKILL.md"
                } else {
                    relativePath
                }
                skillFiles[targetPath] = content
            }

            check(skillStore.saveSkillFileBytes(name, skillFiles)) { "保存失败：$name" }
            importedNames += name
        }
        return importedNames.distinct()
    }

    private fun isInsideNestedSkill(path: String, basePath: String, skillBasePaths: List<String>): Boolean {
        return skillBasePaths.any { otherBasePath ->
            otherBasePath != basePath &&
                isPathInsideBase(path, otherBasePath) &&
                (basePath.isBlank() || isPathInsideBase(otherBasePath, basePath))
        }
    }

    private fun isPathInsideBase(path: String, basePath: String): Boolean {
        return basePath.isBlank() || path == basePath || path.startsWith("$basePath/")
    }

    private fun relativeToSkillBase(path: String, basePath: String): String? {
        if (basePath.isBlank()) return path
        if (path == basePath) return null
        return path.removePrefix("$basePath/").takeIf { it != path }
    }

    private fun isZipFile(fileName: String, bytes: ByteArray): Boolean {
        return fileName.endsWith(".zip", ignoreCase = true) ||
            bytes.startsWithBytes(0x50, 0x4B, 0x03, 0x04) ||
            bytes.startsWithBytes(0x50, 0x4B, 0x05, 0x06) ||
            bytes.startsWithBytes(0x50, 0x4B, 0x07, 0x08)
    }

    private fun ByteArray.startsWithBytes(vararg values: Int): Boolean {
        if (size < values.size) return false
        return values.indices.all { index -> (this[index].toInt() and 0xFF) == values[index] }
    }

    private suspend fun listFilesRecursively(
        owner: String,
        repo: String,
        branch: String,
        dirPath: String,
        basePath: String,
        result: MutableList<Pair<String, String>>,
    ): Boolean {
        val apiUrl = "https://api.github.com/repos/$owner/$repo/contents/$dirPath?ref=$branch"
        val json = downloadText(apiUrl) ?: return false
        val items = Json.parseToJsonElement(json).jsonArray
        for (item in items) {
            val value = item.jsonObject
            val type = value.getValue("type").jsonPrimitive.content
            val itemPath = value.getValue("path").jsonPrimitive.content
            val relativePath = itemPath.removePrefix("$basePath/").removePrefix(basePath)
            when (type) {
                "file" -> {
                    val downloadUrl = value["download_url"]?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                        ?: return false
                    result += relativePath to downloadUrl
                }

                "dir" -> {
                    if (!listFilesRecursively(owner, repo, branch, itemPath, basePath, result)) return false
                }
            }
        }
        return true
    }

    private data class GitHubRepoInfo(
        val owner: String,
        val repo: String,
        val branch: String,
        val path: String,
    )

    private fun parseGitHubUrl(url: String): GitHubRepoInfo? {
        val trimmed = url.trim().trimEnd('/')
        val regex = Regex("""https://github\.com/([^/]+)/([^/]+)(?:/tree/([^/]+)(/.*)?)?""")
        val match = regex.matchEntire(trimmed) ?: return null
        return GitHubRepoInfo(
            owner = match.groupValues[1],
            repo = match.groupValues[2],
            branch = match.groupValues[3].ifBlank { "HEAD" },
            path = match.groupValues[4].trimStart('/'),
        )
    }

    private suspend fun downloadText(url: String): String? {
        val response = httpClient.get(url) {
            header(HttpHeaders.Accept, "application/vnd.github+json")
        }
        return response.takeIf { it.status.isSuccess() }?.bodyAsText()
    }
}
