package me.rerere.rikkahub.ui.pages.translator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ClipboardCopy
import com.composables.icons.lucide.ClipboardPaste
import com.composables.icons.lucide.Languages
import com.composables.icons.lucide.Lucide
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ModelType
import me.rerere.common.utils.getText
import me.rerere.common.utils.provideClipEntry
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import rikkahub.composeapp.generated.resources.*

@Composable
fun TranslatorPage(vm: TranslatorVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val inputText by vm.inputText.collectAsStateWithLifecycle()
    val translatedText by vm.translatedText.collectAsStateWithLifecycle()
    val targetLanguage by vm.targetLanguage.collectAsStateWithLifecycle()
    val translating by vm.translating.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()

    // 处理错误
    LaunchedEffect(Unit) {
        vm.errorFlow.collect { error ->
            toaster.show(error.message ?: "错误", type = ToastType.Error)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(Res.string.translator_page_title))
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    ModelSelector(
                        modelId = settings.translateModeId,
                        onSelect = {
                            vm.updateSettings(settings.copy(translateModeId = it.id))
                        },
                        providers = settings.providers,
                        type = ModelType.CHAT,
                        onlyIcon = true,
                    )
                }
            )
        },
        bottomBar = {
            BottomBar(
                translating = translating,
                onTranslate = {
                    vm.translate()
                },
                onCancelTranslation = {
                    vm.cancelTranslation()
                },
                onLanguageSelected = {
                    vm.updateTargetLanguage(it)
                },
                targetLanguage = targetLanguage
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 输入区域
            Column {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { vm.updateInputText(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(Res.string.translator_page_input_placeholder)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent
                    ),
                    maxLines = 10,
                    textStyle = MaterialTheme.typography.headlineSmall,
                )

                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            clipboard.getClipEntry()?.getText()?.let {
                                vm.updateInputText(it)
                            }
                        }
                    }
                ) {
                    Icon(Lucide.ClipboardPaste, null)
                    Text("粘贴文本", modifier = Modifier.padding(start = 4.dp))
                }
            }

            // 翻译进度条
            Crossfade(translating) { isTranslating ->
                if (isTranslating) {
                    LinearWavyProgressIndicator(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth()
                    )
                } else {
                    HorizontalDivider()
                }
            }

            // 翻译结果
            SelectionContainer {
                Text(
                    text = translatedText.ifEmpty {
                        stringResource(Res.string.translator_page_result_placeholder)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }

            AnimatedVisibility(translatedText.isNotBlank()) {
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(
                                provideClipEntry(null, translatedText)
                            )
                        }
                    }
                ) {
                    Icon(Lucide.ClipboardCopy, null)
                    Text("复制翻译结果", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

private val Locales by lazy {
    listOf(
        Locale.SIMPLIFIED_CHINESE,
        Locale.ENGLISH,
        Locale.TRADITIONAL_CHINESE,
        Locale.JAPANESE,
        Locale.KOREAN,
        Locale.FRENCH,
        Locale.GERMAN,
        Locale.ITALIAN,
        Locale.SPANISH
    )
}

@Composable
private fun LanguageSelector(
    targetLanguage: Locale,
    onLanguageSelected: (Locale) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    @Composable
    fun getLanguageDisplayName(locale: Locale): String {
        return when (locale) {
            Locale.SIMPLIFIED_CHINESE -> stringResource(Res.string.language_simplified_chinese)
            Locale.ENGLISH -> stringResource(Res.string.language_english)
            Locale.TRADITIONAL_CHINESE -> stringResource(Res.string.language_traditional_chinese)
            Locale.JAPANESE -> stringResource(Res.string.language_japanese)
            Locale.KOREAN -> stringResource(Res.string.language_korean)
            Locale.FRENCH -> stringResource(Res.string.language_french)
            Locale.GERMAN -> stringResource(Res.string.language_german)
            Locale.ITALIAN -> stringResource(Res.string.language_italian)
            Locale.SPANISH -> stringResource(Res.string.language_spanish)
            else -> locale.getDisplayLanguage(Locale.current)
        }
    }

    Box(
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = getLanguageDisplayName(targetLanguage),
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                    .fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent
                )
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                Locales.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(getLanguageDisplayName(language)) },
                        onClick = {
                            onLanguageSelected(language)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBar(
    targetLanguage: Locale,
    onLanguageSelected: (Locale) -> Unit,
    translating: Boolean,
    onTranslate: () -> Unit,
    onCancelTranslation: () -> Unit
) {
    BottomAppBar(
        actions = {
            // 目标语言选择
            LanguageSelector(
                targetLanguage = targetLanguage,
                onLanguageSelected = { onLanguageSelected(it) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (translating) {
                        onCancelTranslation()
                    } else {
                        onTranslate()
                    }
                },
                containerColor = BottomAppBarDefaults.bottomAppBarFabColor,
                elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation()
            ) {
                if (!translating) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(
                            Lucide.Languages,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            stringResource(Res.string.translator_page_translate),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                } else {
                    Text(stringResource(Res.string.translator_page_cancel))
                }
            }
        }
    )
}
