package me.rerere.rikkahub.ui.pages.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.sync.WebDavBackupItem
import me.rerere.rikkahub.data.sync.WebdavSync
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.UiState

private const val TAG = "BackupVM"

class BackupVM(
    private val settingsStore: SettingsStore,
    private val webdavSync: WebdavSync,
) : ViewModel() {
    val settings = settingsStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = Settings.dummy()
    )

    val webDavBackupItems = MutableStateFlow<UiState<List<WebDavBackupItem>>>(UiState.Idle)

    init {
        loadBackupFileItems()
    }

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsStore.update(settings)
        }
    }

    fun loadBackupFileItems() {
        viewModelScope.launch {
            runCatching {
                webDavBackupItems.emit(UiState.Loading)
                webDavBackupItems.emit(
                    value = UiState.Success(
                        data = webdavSync.listBackupFiles(
                            webDavConfig = settings.value.webDavConfig
                        ).sortedByDescending { it.lastModified }
                    )
                )
            }.onFailure {
                webDavBackupItems.emit(UiState.Error(it))
            }
        }
    }

    suspend fun testWebDav() {
        webdavSync.testWebdav(settings.value.webDavConfig)
    }

    suspend fun backup() {
        webdavSync.backupToWebDav(settings.value.webDavConfig)
    }

    suspend fun restore(item: WebDavBackupItem) {
        webdavSync.restoreFromWebDav(webDavConfig = settings.value.webDavConfig, item = item)
    }

    suspend fun deleteWebDavBackupFile(item: WebDavBackupItem) {
        webdavSync.deleteWebDavBackupFile(settings.value.webDavConfig, item)
    }

    suspend fun exportToFile(): PlatformFile {
        return webdavSync.prepareBackupFile(settings.value.webDavConfig.copy())
    }

    suspend fun restoreFromLocalFile(file: PlatformFile) {
        webdavSync.restoreFromLocalFile(file, settings.value.webDavConfig)
    }

    suspend fun restoreFromChatBox(file: PlatformFile) {
        val importProviders = arrayListOf<ProviderSetting>()

        val jsonElements = JsonInstant.parseToJsonElement(
            withContext(Dispatchers.IO) { file.readString() }
        ).jsonObject
        val settingsObj = jsonElements["settings"]?.jsonObject
        if (settingsObj != null) {
            settingsObj["providers"]?.jsonObject?.let { providers ->
                providers["openai"]?.jsonObject?.let { openai ->
                    val apiHost = openai["apiHost"]?.jsonPrimitive?.contentOrNull ?: "https://api.openai.com"
                    val apiKey = openai["apiKey"]?.jsonPrimitive?.contentOrNull ?: ""
                    val models = openai["models"]?.jsonArray?.map { element ->
                        val modelId = element.jsonObject["modelId"]?.jsonPrimitive?.contentOrNull ?: ""
                        val capabilities =
                            element.jsonObject["capabilities"]?.jsonArray?.map { it.jsonPrimitive.contentOrNull }
                                ?: emptyList()
                        Model(
                            modelId = modelId,
                            displayName = modelId,
                            inputModalities = buildList {
                                if (capabilities.contains("vision")) {
                                    add(Modality.IMAGE)
                                }
                            },
                            abilities = buildList {
                                if (capabilities.contains("tool_use")) {
                                    add(ModelAbility.TOOL)
                                }
                                if (capabilities.contains("reasoning")) {
                                    add(ModelAbility.REASONING)
                                }
                            }
                        )
                    } ?: emptyList()
                    if (apiKey.isNotBlank()) importProviders.add(
                        ProviderSetting.OpenAI(
                            name = "OpenAI",
                            baseUrl = "$apiHost/v1",
                            apiKey = apiKey,
                            models = models,
                        )
                    )
                }
                providers["claude"]?.jsonObject?.let { claude ->
                    val apiHost =
                        claude["apiHost"]?.jsonPrimitive?.contentOrNull ?: "https://api.anthropic.com"
                    val apiKey = claude["apiKey"]?.jsonPrimitive?.contentOrNull ?: ""
                    if (apiKey.isNotBlank()) importProviders.add(
                        ProviderSetting.Claude(
                            name = "Claude",
                            baseUrl = "${apiHost}/v1",
                            apiKey = apiKey,
                        )
                    )
                }
                providers["gemini"]?.jsonObject?.let { gemini ->
                    val apiHost = gemini["apiHost"]?.jsonPrimitive?.contentOrNull
                        ?: "https://generativelanguage.googleapis.com"
                    val apiKey = gemini["apiKey"]?.jsonPrimitive?.contentOrNull ?: ""
                    if (apiKey.isNotBlank()) importProviders.add(
                        ProviderSetting.Google(
                            name = "Gemini",
                            baseUrl = "$apiHost/v1beta",
                            apiKey = apiKey,
                        )
                    )
                }
            }
        }

        Logger.i(TAG) { "restoreFromChatBox: import ${importProviders.size} providers: $importProviders" }

        updateSettings(
            settings.value.copy(
                providers = importProviders + settings.value.providers,
            )
        )
    }
}
