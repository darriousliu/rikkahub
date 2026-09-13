package me.rerere.rikkahub

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable
sealed interface Screen : NavKey {
    @Serializable
    data class Chat(
        val id: String,
        val text: String? = null,
        val files: List<String> = emptyList(),
        val nodeId: String? = null
    ) : Screen

    @Serializable
    data class ShareHandler(val text: String, val streamUri: String? = null) : Screen

    @Serializable
    data object History : Screen

    @Serializable
    data object Favorite : Screen

    @Serializable
    data object Assistant : Screen

    @Serializable
    data class AssistantDetail(val id: String) : Screen

    @Serializable
    data class AssistantBasic(val id: String) : Screen

    @Serializable
    data class AssistantPrompt(val id: String) : Screen

    @Serializable
    data class AssistantMemory(val id: String) : Screen

    @Serializable
    data class AssistantRequest(val id: String) : Screen

    @Serializable
    data class AssistantMcp(val id: String) : Screen

    @Serializable
    data class AssistantLocalTool(val id: String) : Screen

    @Serializable
    data class AssistantInjections(val id: String) : Screen

    @Serializable
    data object Translator : Screen

    @Serializable
    data object Setting : Screen

    @Serializable
    data object Backup : Screen

    @Serializable
    data object ImageGen : Screen

    @Serializable
    data class WebView(val url: String = "", val contentId: String = "") : Screen

    @Serializable
    data object SettingTheme : Screen

    @Serializable
    data object SettingPreferences : Screen

    @Serializable
    data object SettingPreferencesTheme : Screen

    @Serializable
    data object SettingPreferencesNotification : Screen

    @Serializable
    data object SettingPreferencesGeneral : Screen

    @Serializable
    data object SettingPreferencesUI : Screen

    @Serializable
    data object SettingProvider : Screen

    @Serializable
    data class SettingProviderDetail(val providerId: String) : Screen

    @Serializable
    data object SettingModels : Screen

    @Serializable
    data object SettingAbout : Screen

    @Serializable
    data object SettingSearch : Screen

    @Serializable
    data class SettingSearchDetail(val serviceId: String) : Screen

    @Serializable
    data object SettingSpeech : Screen

    @Serializable
    data object SettingMcp : Screen

    @Serializable
    data object SettingDonate : Screen

    @Serializable
    data object SettingFiles : Screen

    @Serializable
    data object SettingWeb : Screen

    @Serializable
    data object Debug : Screen

    @Serializable
    data object Log : Screen

    @Serializable
    data object Extensions : Screen

    @Serializable
    data object QuickMessages : Screen

    @Serializable
    data object Prompts : Screen

    @Serializable
    data object Skills : Screen

    @Serializable
    data object Workspaces : Screen

    @Serializable
    data class WorkspaceDetail(val id: String) : Screen

    @Serializable
    data class WorkspaceTerminal(val id: String) : Screen

    @Serializable
    data class WorkspaceFileEditor(val id: String, val area: String, val path: String) : Screen

    @Serializable
    data class SkillDetail(val skillName: String) : Screen

    @Serializable
    data object MessageSearch : Screen

    @Serializable
    data object Stats : Screen
}

internal val navigationSavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Screen.Chat::class, Screen.Chat.serializer())
            subclass(Screen.ShareHandler::class, Screen.ShareHandler.serializer())
            subclass(Screen.History::class, Screen.History.serializer())
            subclass(Screen.Favorite::class, Screen.Favorite.serializer())
            subclass(Screen.Assistant::class, Screen.Assistant.serializer())
            subclass(Screen.AssistantDetail::class, Screen.AssistantDetail.serializer())
            subclass(Screen.AssistantBasic::class, Screen.AssistantBasic.serializer())
            subclass(Screen.AssistantPrompt::class, Screen.AssistantPrompt.serializer())
            subclass(Screen.AssistantMemory::class, Screen.AssistantMemory.serializer())
            subclass(Screen.AssistantRequest::class, Screen.AssistantRequest.serializer())
            subclass(Screen.AssistantMcp::class, Screen.AssistantMcp.serializer())
            subclass(Screen.AssistantLocalTool::class, Screen.AssistantLocalTool.serializer())
            subclass(Screen.AssistantInjections::class, Screen.AssistantInjections.serializer())
            subclass(Screen.Translator::class, Screen.Translator.serializer())
            subclass(Screen.Setting::class, Screen.Setting.serializer())
            subclass(Screen.Backup::class, Screen.Backup.serializer())
            subclass(Screen.ImageGen::class, Screen.ImageGen.serializer())
            subclass(Screen.WebView::class, Screen.WebView.serializer())
            subclass(Screen.SettingTheme::class, Screen.SettingTheme.serializer())
            subclass(Screen.SettingPreferences::class, Screen.SettingPreferences.serializer())
            subclass(Screen.SettingPreferencesTheme::class, Screen.SettingPreferencesTheme.serializer())
            subclass(Screen.SettingPreferencesNotification::class, Screen.SettingPreferencesNotification.serializer())
            subclass(Screen.SettingPreferencesGeneral::class, Screen.SettingPreferencesGeneral.serializer())
            subclass(Screen.SettingPreferencesUI::class, Screen.SettingPreferencesUI.serializer())
            subclass(Screen.SettingProvider::class, Screen.SettingProvider.serializer())
            subclass(Screen.SettingProviderDetail::class, Screen.SettingProviderDetail.serializer())
            subclass(Screen.SettingModels::class, Screen.SettingModels.serializer())
            subclass(Screen.SettingAbout::class, Screen.SettingAbout.serializer())
            subclass(Screen.SettingSearch::class, Screen.SettingSearch.serializer())
            subclass(Screen.SettingSearchDetail::class, Screen.SettingSearchDetail.serializer())
            subclass(Screen.SettingSpeech::class, Screen.SettingSpeech.serializer())
            subclass(Screen.SettingMcp::class, Screen.SettingMcp.serializer())
            subclass(Screen.SettingDonate::class, Screen.SettingDonate.serializer())
            subclass(Screen.SettingFiles::class, Screen.SettingFiles.serializer())
            subclass(Screen.SettingWeb::class, Screen.SettingWeb.serializer())
            subclass(Screen.Debug::class, Screen.Debug.serializer())
            subclass(Screen.Log::class, Screen.Log.serializer())
            subclass(Screen.Extensions::class, Screen.Extensions.serializer())
            subclass(Screen.QuickMessages::class, Screen.QuickMessages.serializer())
            subclass(Screen.Prompts::class, Screen.Prompts.serializer())
            subclass(Screen.Skills::class, Screen.Skills.serializer())
            subclass(Screen.Workspaces::class, Screen.Workspaces.serializer())
            subclass(Screen.WorkspaceDetail::class, Screen.WorkspaceDetail.serializer())
            subclass(Screen.WorkspaceTerminal::class, Screen.WorkspaceTerminal.serializer())
            subclass(Screen.WorkspaceFileEditor::class, Screen.WorkspaceFileEditor.serializer())
            subclass(Screen.SkillDetail::class, Screen.SkillDetail.serializer())
            subclass(Screen.MessageSearch::class, Screen.MessageSearch.serializer())
            subclass(Screen.Stats::class, Screen.Stats.serializer())
        }
    }
}
