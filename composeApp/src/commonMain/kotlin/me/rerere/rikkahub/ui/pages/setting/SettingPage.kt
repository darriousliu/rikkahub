package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiMagic
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.Book01
import me.rerere.hugeicons.stroke.Book03
import me.rerere.hugeicons.stroke.Bookshelf01
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Clapping01
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.GlobalSearch
import me.rerere.hugeicons.stroke.ImageUpload
import me.rerere.hugeicons.stroke.InLove
import me.rerere.hugeicons.stroke.LookTop
import me.rerere.hugeicons.stroke.McpServer
import me.rerere.hugeicons.stroke.Megaphone01
import me.rerere.hugeicons.stroke.Package
import me.rerere.hugeicons.stroke.ServerStack01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Share04
import me.rerere.hugeicons.stroke.Sun01
import me.rerere.hugeicons.stroke.WavingHand01
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.isNotConfigured
import me.rerere.rikkahub.generated.resources.*
import me.rerere.rikkahub.platform.ExternalUriOpener
import me.rerere.rikkahub.platform.rememberPlatformTextSharer
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.components.ui.icons.DiscordIcon
import me.rerere.rikkahub.ui.components.ui.icons.TencentQQIcon
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.rememberColorMode
import me.rerere.rikkahub.ui.hooks.ColorMode
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import me.rerere.rikkahub.utils.toFixed
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingPage(vm: SettingVM = koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val externalUriOpener: ExternalUriOpener = koinInject()
    val storageSummaryProvider: ChatStorageSummaryProvider = koinInject()
    val textSharer = rememberPlatformTextSharer()

    if (settings.launchCount > 100 && (settings.launchCount - settings.sponsorAlertDismissedAt) >= 50) {
        AlertDialog(
            onDismissRequest = {
                vm.updateSettings(settings.copy(sponsorAlertDismissedAt = settings.launchCount))
            },
            icon = { Icon(HugeIcons.WavingHand01, null) },
            title = { Text(stringResource(Res.string.setting_page_sponsor_alert_title)) },
            text = { Text(stringResource(Res.string.setting_page_sponsor_alert_desc)) },
            confirmButton = {
                Button(onClick = {
                    vm.updateSettings(settings.copy(sponsorAlertDismissedAt = settings.launchCount))
                    navController.navigate(Screen.SettingDonate)
                }) {
                    Text(stringResource(Res.string.setting_page_sponsor_alert_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.updateSettings(settings.copy(sponsorAlertDismissedAt = settings.launchCount))
                }) {
                    Text(stringResource(Res.string.setting_page_sponsor_alert_dismiss))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(text = stringResource(Res.string.settings))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (settings.isNotConfigured()) {
                item {
                    ProviderConfigWarningCard(navController)
                }
            }

            item("generalSettings") {
                var colorMode by rememberColorMode()
                val selectedColorModeText = when (colorMode) {
                    ColorMode.SYSTEM -> stringResource(Res.string.setting_page_color_mode_system)
                    ColorMode.LIGHT -> stringResource(Res.string.setting_page_color_mode_light)
                    ColorMode.DARK -> stringResource(Res.string.setting_page_color_mode_dark)
                }
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(Res.string.setting_page_general_settings)) },
                ) {
                    item(
                        leadingContent = { Icon(HugeIcons.Sun01, null) },
                        trailingContent = {
                            Select(
                                options = ColorMode.entries,
                                selectedOption = colorMode,
                                onOptionSelected = {
                                    colorMode = it
                                    navController.navigate(Screen.Setting) {
                                        popUpTo(Screen.Setting) {
                                            inclusive = true
                                        }
                                    }
                                },
                                optionToString = {
                                    when (it) {
                                        ColorMode.SYSTEM -> stringResource(Res.string.setting_page_color_mode_system)
                                        ColorMode.LIGHT -> stringResource(Res.string.setting_page_color_mode_light)
                                        ColorMode.DARK -> stringResource(Res.string.setting_page_color_mode_dark)
                                    }
                                },
                                modifier = Modifier.width(150.dp)
                            )
                        },
                        headlineContent = { Text(stringResource(Res.string.setting_page_color_mode)) },
                        supportingContent = { Text(selectedColorModeText) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferences) },
                        leadingContent = { Icon(HugeIcons.Settings03, null) },
                        supportingContent = { Text(stringResource(Res.string.setting_page_preferences_desc)) },
                        headlineContent = { Text(stringResource(Res.string.setting_page_preferences)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Assistant) },
                        leadingContent = { Icon(HugeIcons.LookTop, null) },
                        supportingContent = { Text(stringResource(Res.string.setting_page_assistant_desc)) },
                        headlineContent = { Text(stringResource(Res.string.setting_page_assistant)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Extensions) },
                        leadingContent = { Icon(HugeIcons.Package, null) },
                        supportingContent = { Text(stringResource(Res.string.setting_page_extensions_desc)) },
                        headlineContent = { Text(stringResource(Res.string.setting_page_extensions)) },
                    )
                }
            }

            item("modelServices") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(Res.string.setting_page_model_and_services)) },
                ) {
                    item(
                        onClick = { navController.navigate(Screen.SettingModels) },
                        leadingContent = { Icon(HugeIcons.AiMagic, null) },
                        supportingContent = { Text(stringResource(Res.string.setting_page_default_model_desc)) },
                        headlineContent = { Text(stringResource(Res.string.setting_page_default_model)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingProvider) },
                        leadingContent = { Icon(HugeIcons.Brain02, null) },
                        supportingContent = { Text(stringResource(Res.string.setting_page_providers_desc)) },
                        headlineContent = { Text(stringResource(Res.string.setting_page_providers)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingSearch) },
                        leadingContent = { Icon(HugeIcons.GlobalSearch, null) },
                        supportingContent = { Text(stringResource(Res.string.setting_page_search_service_desc)) },
                        headlineContent = { Text(stringResource(Res.string.setting_page_search_service)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingSpeech) },
                        leadingContent = { Icon(HugeIcons.Megaphone01, null) },
                        supportingContent = { Text(stringResource(Res.string.setting_page_tts_service_desc)) },
                        headlineContent = { Text(stringResource(Res.string.setting_page_tts_service)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingMcp) },
                        leadingContent = { Icon(HugeIcons.McpServer, null) },
                        supportingContent = { Text(stringResource(Res.string.setting_page_mcp_desc)) },
                        headlineContent = { Text(stringResource(Res.string.setting_page_mcp)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingWeb) },
                        leadingContent = { Icon(HugeIcons.ServerStack01, null) },
                        supportingContent = { Text(stringResource(Res.string.setting_page_web_server_desc)) },
                        headlineContent = { Text(stringResource(Res.string.setting_page_web_server)) },
                    )
                }
            }

            item("dataSettings") {
                val storageState by produceState<ChatStorageSummary?>(null, storageSummaryProvider) {
                    value = storageSummaryProvider.load()
                }
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(Res.string.setting_page_data_settings)) },
                ) {
                    item(
                        onClick = { navController.navigate(Screen.Backup) },
                        leadingContent = { Icon(HugeIcons.Database02, null) },
                        supportingContent = { Text(stringResource(Res.string.setting_page_data_backup_desc)) },
                        headlineContent = { Text(stringResource(Res.string.setting_page_data_backup)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingFiles) },
                        leadingContent = { Icon(HugeIcons.ImageUpload, null) },
                        supportingContent = {
                            val summary = storageState
                            if (summary == null) {
                                Text(stringResource(Res.string.calculating))
                            } else {
                                Text(
                                    stringResource(
                                        Res.string.setting_page_chat_storage_desc,
                                        summary.fileCount,
                                        (summary.totalBytes / 1024 / 1024.0).toFixed(2)
                                    )
                                )
                            }
                        },
                        headlineContent = { Text(stringResource(Res.string.setting_page_chat_storage)) },
                    )
                }
            }

            item("aboutSettings") {
                val shareText = stringResource(Res.string.setting_page_share_text)
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(Res.string.setting_page_about)) },
                ) {
                    item(
                        onClick = { navController.navigate(Screen.SettingAbout) },
                        leadingContent = { Icon(HugeIcons.Clapping01, null) },
                        supportingContent = { Text(stringResource(Res.string.setting_page_about_desc)) },
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                var showQQGroupSheet by remember { mutableStateOf(false) }
                                IconButton(
                                    onClick = { showQQGroupSheet = true }
                                ) {
                                    Icon(
                                        imageVector = TencentQQIcon,
                                        contentDescription = "QQ",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                if (showQQGroupSheet) {
                                    QQGroupBottomSheet(
                                        onDismiss = { showQQGroupSheet = false }
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        externalUriOpener.open("https://discord.gg/9weBqxe5c4")
                                    }
                                ) {
                                    Icon(
                                        imageVector = DiscordIcon,
                                        contentDescription = "Discord",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        },
                        headlineContent = { Text(stringResource(Res.string.setting_page_about)) },
                    )
                    item(
                        onClick = {
                            val docUrl = if (Locale.current.language == "zh") {
                                "https://docs.rikka-ai.com/zh/introduction"
                            } else {
                                "https://docs.rikka-ai.com/introduction"
                            }
                            externalUriOpener.open(docUrl)
                        },
                        leadingContent = { Icon(HugeIcons.Book01, null) },
                        supportingContent = { Text(stringResource(Res.string.setting_page_documentation_desc)) },
                        headlineContent = { Text(stringResource(Res.string.setting_page_documentation)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Log) },
                        leadingContent = { Icon(HugeIcons.Bookshelf01, null) },
                        supportingContent = { Text(stringResource(Res.string.setting_page_request_logs_desc)) },
                        headlineContent = { Text(stringResource(Res.string.setting_page_request_logs)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingDonate) },
                        leadingContent = { Icon(HugeIcons.InLove, null) },
                        supportingContent = { Text(stringResource(Res.string.setting_page_donate_desc)) },
                        headlineContent = { Text(stringResource(Res.string.setting_page_donate)) },
                    )
                    item(
                        onClick = {
                            textSharer.share(shareText)
                        },
                        leadingContent = { Icon(HugeIcons.Share04, null) },
                        supportingContent = { Text(stringResource(Res.string.setting_page_share_desc)) },
                        headlineContent = { Text(stringResource(Res.string.setting_page_share)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderConfigWarningCard(navController: Navigator) {
    Card(
        modifier = Modifier.padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            ListItem(
                headlineContent = {
                    Text(stringResource(Res.string.setting_page_config_api_title))
                },
                supportingContent = {
                    Text(stringResource(Res.string.setting_page_config_api_desc))
                },
                leadingContent = {
                    Icon(HugeIcons.Alert01, null)
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent
                )
            )

            TextButton(
                onClick = {
                    navController.navigate(Screen.SettingProvider)
                }
            ) {
                Text(stringResource(Res.string.setting_page_config))
            }
        }
    }
}

private data class QQGroup(
    val name: String,
    val key: String,
)

private val QQ_GROUPS = listOf(
    QQGroup("RikkaHub 一群", "4POE46u9e_zoy1TkNfWdCvueR9CKFJdk"),
    QQGroup("RikkaHub 二群", "Qsm0whzbPsm1UyNpR683ulLyMZ2Pqrw0"),
    QQGroup("RikkaHub 三群", "Qc9oP-9tXioZeQEvEvI2_owWtBAIx3lS"),
)

@Composable
private fun QQGroupBottomSheet(onDismiss: () -> Unit) {
    val externalUriOpener: ExternalUriOpener = koinInject()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            QQ_GROUPS.forEach { group ->
                ListItem(
                    headlineContent = { Text(group.name) },
                    leadingContent = {
                        Icon(
                            imageVector = TencentQQIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    },
                    modifier = Modifier.clickable {
                        externalUriOpener.open(
                            "mqqopensdkapi://bizAgent/qm/qr?" +
                                "url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp" +
                                "%26p%3Dandroid%26jump_from%3Dwebapi%26k%3D${group.key}"
                        )
                        onDismiss()
                    }
                )
            }
        }
    }
}
