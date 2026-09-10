package me.rerere.rikkahub.ui.pages.extensions.skills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemPathSeparator
import me.rerere.rikkahub.data.files.SkillFrontmatterParser
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.utils.isDirectory
import me.rerere.rikkahub.utils.isFile
import me.rerere.rikkahub.utils.listFiles
import me.rerere.rikkahub.utils.readText

data class SkillFile(
    val file: Path,
    val relativePath: String,
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
    private val skillManager: SkillManager,
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
        viewModelScope.launch(Dispatchers.IO) {
            val dir = skillManager.getSkillDir(resolveDirectory()) ?: return@launch
            _tree.value = buildTree(dir, dir)
        }
    }

    fun readFile(skillFile: SkillFile, onResult: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val content = skillManager.resolveSkillFile(resolveDirectory(), skillFile.relativePath)
                ?.takeIf { it.isFile }?.readText()
            withContext(Dispatchers.Main) { onResult(content) }
        }
    }

    fun saveFile(relativePath: String, content: String, onResult: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (relativePath == "SKILL.md") {
                val name = SkillFrontmatterParser.parse(content)["name"]
                if (name != displayName) {
                    withContext(Dispatchers.Main) {
                        onResult("不允许修改技能名称（name 字段必须为 \"$displayName\"）")
                    }
                    return@launch
                }
            }
            val success = skillManager.saveSkillFile(resolveDirectory(), relativePath, content)
            loadFiles()
            withContext(Dispatchers.Main) { onResult(if (success) null else "保存失败") }
        }
    }

    fun deleteFile(skillFile: SkillFile, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = skillManager.deleteSkillFile(resolveDirectory(), skillFile.relativePath)
            if (success) loadFiles()
            withContext(Dispatchers.Main) { onResult(success) }
        }
    }

    /**
     * 导航只带显示名，而显示名可能与目录名不同。解析一次并缓存；找不到时退回显示名，
     * 与修复前的行为一致。
     */
    private fun resolveDirectory(): String {
        directoryName?.let { return it }
        val resolved = skillManager.listSkills()
            .firstOrNull { summary -> summary.name == displayName }
            ?.skillDir?.name
            ?: displayName
        directoryName = resolved
        return resolved
    }

    private fun buildTree(root: Path, dir: Path): List<SkillFileNode> {
        val items = dir.listFiles() ?: return emptyList()
        val files = items
            .filter { it.isFile }
            .sortedWith(compareBy({ it.name != "SKILL.md" }, { it.name }))
            .map { f -> SkillFileNode.FileNode(SkillFile(f, f.toString().removePrefix("$root$SystemPathSeparator"))) }
        val dirs = items
            .filter { it.isDirectory }
            .sortedBy { it.name }
            .map { d ->
                SkillFileNode.DirNode(
                    d.name, d.toString().removePrefix("$root$SystemPathSeparator"), buildTree(root, d),
                )
            }
        return dirs + files
    }
}
