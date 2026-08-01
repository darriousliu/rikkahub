package me.rerere.rikkahub.ui.pages.assistant

data class AssistantSkillMetadata(
    val key: String,
    val name: String,
    val description: String,
)

fun interface AssistantSkillCatalog {
    suspend fun listSkills(): List<AssistantSkillMetadata>
}
