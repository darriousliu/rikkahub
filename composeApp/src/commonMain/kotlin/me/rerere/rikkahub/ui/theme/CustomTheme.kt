package me.rerere.rikkahub.ui.theme

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class CustomTheme(
    val id: String = Uuid.random().toString(),
    val name: String = "",
    val primaryColorArgb: Long = 0xFF6750A4,
    val secondaryColorArgb: Long? = null,
    val tertiaryColorArgb: Long? = null,
)
