package me.rerere.rikkahub.data.files

import kotlinx.io.files.Path
import kotlinx.io.files.SystemPathSeparator
import me.rerere.rikkahub.utils.canonicalFile
import me.rerere.rikkahub.utils.resolve

object SkillPaths {
    fun resolveSkillDir(skillsRoot: Path, skillName: String): Path? {
        if (skillName.isBlank()) return null
        if (skillName == "." || skillName == "..") return null
        if (skillName.contains('/') || skillName.contains('\\')) return null

        val canonicalRoot = skillsRoot.canonicalFile
        val canonicalDir = canonicalRoot.resolve(skillName).canonicalFile
        val parent = canonicalDir.parent ?: return null

        if (parent != canonicalRoot) return null
        if (!canonicalDir.isSameOrInside(canonicalRoot)) return null

        return canonicalDir
    }

    fun resolveSkillFile(skillDir: Path, relativePath: String): Path? {
        if (relativePath.isBlank()) return null

        val canonicalSkillDir = skillDir.canonicalFile
        val canonicalTarget = canonicalSkillDir.resolve(relativePath).canonicalFile

        return canonicalTarget.takeIf { it.isSameOrInside(canonicalSkillDir) }
    }

    private fun Path.isSameOrInside(root: Path): Boolean {
        val rootPath = root.canonicalFile.toString()
        val currentPath = canonicalFile.toString()
        return currentPath == rootPath || currentPath.startsWith(rootPath + SystemPathSeparator)
    }
}
