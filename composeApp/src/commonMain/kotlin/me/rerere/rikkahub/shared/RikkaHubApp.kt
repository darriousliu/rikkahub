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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.layout.currentWindowDpSize

internal enum class SharedNavigationPresentation {
    BottomBar,
    NavigationRail,
}

internal fun selectSharedNavigationPresentation(widthDp: Float): SharedNavigationPresentation =
    if (widthDp >= 840f) SharedNavigationPresentation.NavigationRail else SharedNavigationPresentation.BottomBar

private enum class BootstrapDestination(
    val label: String,
    val shortLabel: String,
) {
    Status("Status", "S"),
    Capabilities("Capabilities", "C"),
}

/** Shared application root hosted by Android, iOS and Desktop shells. */
@Composable
fun RikkaHubApp(productContent: (@Composable () -> Unit)? = null) {
    if (productContent != null) {
        productContent()
        return
    }

    val platform = currentPlatformKind
    val windowSize = currentWindowDpSize()
    val presentation = selectSharedNavigationPresentation(windowSize.width.value)
    var destination by rememberSaveable { mutableStateOf(BootstrapDestination.Status) }

    MaterialTheme {
        Scaffold(
            modifier = Modifier.testTag(SharedEntryTestTags.Root),
            topBar = {
                TopAppBar(title = { Text("RikkaHub") })
            },
            bottomBar = {
                if (presentation == SharedNavigationPresentation.BottomBar) {
                    NavigationBar {
                        BootstrapDestination.entries.forEach { item ->
                            NavigationBarItem(
                                selected = destination == item,
                                onClick = { destination = item },
                                icon = { Text(item.shortLabel) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (presentation == SharedNavigationPresentation.NavigationRail) {
                    NavigationRail {
                        BootstrapDestination.entries.forEach { item ->
                            NavigationRailItem(
                                selected = destination == item,
                                onClick = { destination = item },
                                icon = { Text(item.shortLabel) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
                Surface(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (destination) {
                        BootstrapDestination.Status -> SharedStartupStatus(platform)
                        BootstrapDestination.Capabilities -> SharedCapabilityList(platform)
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedStartupStatus(platform: PlatformKind) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Shared application is starting…",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Platform: ${platform.displayName}",
            modifier = Modifier.testTag(SharedEntryTestTags.Platform),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Product screens are connected here as each feature domain completes migration.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun SharedCapabilityList(platform: PlatformKind) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Capabilities",
            style = MaterialTheme.typography.headlineSmall,
        )
        capabilityMatrix(platform).forEach { (capability, state) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SharedEntryTestTags.capability(capability)),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(capability.displayName, style = MaterialTheme.typography.bodyMedium)
                Text(state.displayName, style = MaterialTheme.typography.labelLarge)
            }
            HorizontalDivider()
        }
    }
}
