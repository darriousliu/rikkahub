package me.rerere.rikkahub.ui.pages.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.files.SkillManager

class AndroidAssistantSkillCatalog(
    private val skillManager: SkillManager,
) : AssistantSkillCatalog {
    override suspend fun listSkills(): List<AssistantSkillMetadata> = withContext(Dispatchers.IO) {
        skillManager.listSkills().map { skill ->
            AssistantSkillMetadata(
                key = skill.skillDir.absolutePath,
                name = skill.name,
                description = skill.description,
            )
        }
    }
}
