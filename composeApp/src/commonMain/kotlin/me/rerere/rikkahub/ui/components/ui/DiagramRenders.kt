package me.rerere.rikkahub.ui.components.ui

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.Deferred

// Export hosts wait until every native diagram has either rendered or displayed its error.
internal val LocalDiagramRenders = staticCompositionLocalOf<MutableSet<Deferred<Unit>>?> { null }
