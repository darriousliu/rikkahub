package me.rerere.rikkahub.data.ai.transformers

import androidx.compose.material3.Text
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import org.jetbrains.compose.resources.stringResource
import platform.Foundation.*
import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPad
import rikkahub.composeapp.generated.resources.*

actual object DefaultPlaceholderProvider : PlaceholderProvider {
    actual override val placeholders: Map<String, PlaceholderInfo> = buildPlaceholders {
        val nsDate = NSDate()
        placeholder("cur_date", { Text(stringResource(Res.string.placeholder_current_date)) }) {
            nsDate.toDateString()
        }

        placeholder("cur_time", { Text(stringResource(Res.string.placeholder_current_time)) }) {
            nsDate.toTimeString()
        }

        placeholder("cur_datetime", { Text(stringResource(Res.string.placeholder_current_datetime)) }) {
            nsDate.toDateTimeString()
        }

        placeholder("model_id", { Text(stringResource(Res.string.placeholder_model_id)) }) {
            it.model.modelId
        }

        placeholder("model_name", { Text(stringResource(Res.string.placeholder_model_name)) }) {
            it.model.displayName
        }

        placeholder("locale", { Text(stringResource(Res.string.placeholder_locale)) }) {
            NSLocale.currentLocale.displayNameForKey(NSLocaleIdentifier, NSLocale.currentLocale.localeIdentifier)
                ?: "Unknown"
        }

        placeholder("timezone", { Text(stringResource(Res.string.placeholder_timezone)) }) {
            NSTimeZone.defaultTimeZone.localizedName(
                style = NSTimeZoneNameStyle.NSTimeZoneNameStyleStandard,
                locale = NSLocale.currentLocale
            ) ?: "Unknown"
        }

        placeholder("system_version", { Text(stringResource(Res.string.placeholder_system_version)) }) {
            val device = UIDevice.currentDevice
            val systemName = when (device.userInterfaceIdiom) {
                UIUserInterfaceIdiomPad -> "iPadOS"
                else -> "iOS"
            }
            "$systemName ${device.systemVersion}"
        }

        placeholder("device_info", { Text(stringResource(Res.string.placeholder_device_info)) }) {
            "${UIDevice.currentDevice.model} (${UIDevice.currentDevice.name})"
        }

        placeholder("battery_level", { Text(stringResource(Res.string.placeholder_battery_level)) }) {
            val device = UIDevice.currentDevice
            device.batteryMonitoringEnabled = true
            (device.batteryLevel * 100).toInt().coerceIn(0..100).toString()
        }

        placeholder("nickname", { Text(stringResource(Res.string.placeholder_nickname)) }) {
            it.settingsStore.settingsFlow.value.displaySetting.userNickname.ifBlank { "user" }
        }

        placeholder("char", { Text(stringResource(Res.string.placeholder_char)) }) {
            it.settingsStore.settingsFlow.value.getCurrentAssistant().name.ifBlank { "assistant" }
        }

        placeholder("user", { Text(stringResource(Res.string.placeholder_user)) }) {
            it.settingsStore.settingsFlow.value.displaySetting.userNickname.ifBlank { "user" }
        }
    }

    private fun NSDate.toDateString(): String {
        val formatter = NSDateFormatter().apply {
            dateStyle = NSDateFormatterMediumStyle
            timeStyle = NSDateFormatterNoStyle
            locale = NSLocale.currentLocale
        }
        return formatter.stringFromDate(this)
    }

    private fun NSDate.toTimeString(): String {
        val formatter = NSDateFormatter().apply {
            dateStyle = NSDateFormatterNoStyle
            timeStyle = NSDateFormatterMediumStyle
            locale = NSLocale.currentLocale
        }
        return formatter.stringFromDate(this)
    }

    private fun NSDate.toDateTimeString(): String {
        val formatter = NSDateFormatter().apply {
            dateStyle = NSDateFormatterMediumStyle
            timeStyle = NSDateFormatterMediumStyle
            locale = NSLocale.currentLocale
        }
        return formatter.stringFromDate(this)
    }
}
