package me.rerere.rikkahub.ui.pages.extensions.skills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.files.SkillFrontmatterParser
import me.rerere.rikkahub.data.files.SkillStore
import me.rerere.rikkahub.data.files.StoredSkillFile

data class SkillFile(
    val name: String,
    val relativePath: String,
    val size: Long,
)

sealed class SkillFileNode {
    data class FileNode(val skillFile: SkillFile) : SkillFileNode()

    data class DirNode(
        val name: String,
        val relativePath: String,
        val children: List<SkillFileNode>,
    ) : SkillFileNode()
}

class SkillDetailVM(
    private val skillStore: SkillStore,
) : ViewModel() {
    private val _tree = MutableStateFlow<List<SkillFileNode>>(emptyList())
    val tree = _tree.asStateFlow()

    /** frontmatter 里的显示名，导航传进来的就是它 */
    private var displayName = ""

    /** 技能所在目录名，所有文件操作都以它为准 */
    private var directoryName: String? = null

    fun init(name: String) {
        if (displayName == name) return
        displayName = name
        directoryName = null
        loadFiles()
    }

    fun loadFiles() {
        viewModelScope.launch {
            _tree.value = buildTree(skillStore.listSkillFiles(resolveDirectory()))
        }
    }

    fun readFile(skillFile: SkillFile, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            onResult(skillStore.readSkillFile(resolveDirectory(), skillFile.relativePath))
        }
    }

    fun saveFile(relativePath: String, content: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            if (relativePath == "SKILL.md") {
                val name = SkillFrontmatterParser.parse(content)["name"]
                if (name != displayName) {
                    onResult("不允许修改技能名称（name 字段必须为 \"$displayName\"）")
                    return@launch
                }
            }
            val success = skillStore.saveSkillFile(resolveDirectory(), relativePath, content)
            loadFiles()
            onResult(if (success) null else "保存失败")
        }
    }

    fun deleteFile(skillFile: SkillFile, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = skillStore.deleteSkillFile(resolveDirectory(), skillFile.relativePath)
            if (success) loadFiles()
            onResult(success)
        }
    }

    /**
     * 导航只带显示名，而显示名可能与目录名不同。解析一次并缓存；找不到时退回显示名，
     * 与修复前的行为一致。
     */
    private suspend fun resolveDirectory(): String {
        directoryName?.let { return it }
        val resolved = skillStore.listSkills()
            .firstOrNull { summary -> summary.name == displayName }
            ?.directoryName
            ?: displayName
        directoryName = resolved
        return resolved
    }

    private fun buildTree(entries: List<StoredSkillFile>, parentPath: String = ""): List<SkillFileNode> {
        val children = entries.filter { entry ->
            entry.relativePath.substringBeforeLast('/', missingDelimiterValue = "") == parentPath
        }
        val directories = children
            .filter(StoredSkillFile::isDirectory)
            .sortedBy(StoredSkillFile::name)
            .map { directory ->
                SkillFileNode.DirNode(
                    name = directory.name,
                    relativePath = directory.relativePath,
                    children = buildTree(entries, directory.relativePath),
                )
            }
        val files = children
            .filterNot(StoredSkillFile::isDirectory)
            .sortedWith(compareBy({ it.name != "SKILL.md" }, StoredSkillFile::name))
            .map { file ->
                SkillFileNode.FileNode(
                    SkillFile(
                        name = file.name,
                        relativePath = file.relativePath,
                        size = file.size,
                    )
                )
            }
        return directories + files
    }
}
