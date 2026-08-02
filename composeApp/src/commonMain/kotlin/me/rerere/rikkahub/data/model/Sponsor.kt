package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable

@Serializable
public data class Sponsor(
    public val userName: String,
    public val avatar: String,
    public val amount: String,
)
