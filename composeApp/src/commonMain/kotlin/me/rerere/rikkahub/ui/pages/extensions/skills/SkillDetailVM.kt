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

    private var skillName = ""

    fun init(name: String) {
        if (skillName == name) return
        skillName = name
        loadFiles()
    }

    fun loadFiles() {
        viewModelScope.launch {
            _tree.value = buildTree(skillStore.listSkillFiles(skillName))
        }
    }

    fun readFile(skillFile: SkillFile, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            onResult(skillStore.readSkillFile(skillName, skillFile.relativePath))
        }
    }

    fun saveFile(relativePath: String, content: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            if (relativePath == "SKILL.md") {
                val name = SkillFrontmatterParser.parse(content)["name"]
                if (name != skillName) {
                    onResult("不允许修改技能名称（name 字段必须为 \"$skillName\"）")
                    return@launch
                }
            }
            val success = skillStore.saveSkillFile(skillName, relativePath, content)
            loadFiles()
            onResult(if (success) null else "保存失败")
        }
    }

    fun deleteFile(skillFile: SkillFile, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = skillStore.deleteSkillFile(skillName, skillFile.relativePath)
            if (success) loadFiles()
            onResult(success)
        }
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
