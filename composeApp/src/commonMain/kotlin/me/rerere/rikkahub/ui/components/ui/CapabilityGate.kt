package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.shared.CapabilityState
import me.rerere.rikkahub.shared.PlatformCapability
import me.rerere.rikkahub.shared.capabilityState
import me.rerere.rikkahub.shared.currentPlatformKind

@Composable
fun CapabilityGate(
    capability: PlatformCapability,
    modifier: Modifier = Modifier,
    unavailableContent: (@Composable (CapabilityState) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val state = capabilityState(currentPlatformKind, capability)
    if (state == CapabilityState.READY) {
        content()
    } else if (unavailableContent != null) {
        unavailableContent(state)
    } else {
        CapabilityUnavailable(
            capability = capability,
            state = state,
            modifier = modifier,
        )
    }
}

@Composable
fun CapabilityUnavailable(
    capability: PlatformCapability,
    state: CapabilityState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("capability_${capability.id}_${state.name.lowercase()}"),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = capability.displayName,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = when (state) {
                    CapabilityState.PENDING -> "This feature is still being prepared for this platform."
                    CapabilityState.UNAVAILABLE -> "This feature is not available on this platform."
                    CapabilityState.READY -> "Ready"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
