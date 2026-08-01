package me.rerere.rikkahub.data.favorite

import me.rerere.rikkahub.data.db.entity.FavoriteEntity
import me.rerere.rikkahub.data.model.FavoriteType
import kotlin.time.Clock

interface FavoriteAdapter<T> {
    val type: FavoriteType

    fun buildRefKey(target: T): String

    fun buildFavoriteEntity(
        target: T,
        existing: FavoriteEntity? = null,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ): FavoriteEntity
}
