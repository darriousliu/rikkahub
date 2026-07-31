package me.rerere.rikkahub.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Minimal shared UI used while product screens are migrated into common code. */
@Composable
public fun RikkaHubApp() {
    val platform = currentPlatformKind

    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag(SharedEntryTestTags.Root),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "RikkaHub",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Shared application is starting…",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Platform: ${platform.displayName}",
                    modifier = Modifier.testTag(SharedEntryTestTags.Platform),
                    style = MaterialTheme.typography.titleMedium,
                )
                HorizontalDivider()
                Text(
                    text = "Capabilities",
                    style = MaterialTheme.typography.titleMedium,
                )
                capabilityMatrix(platform).forEach { (capability, state) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(SharedEntryTestTags.capability(capability)),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = capability.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = state.displayName,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}
