package me.rerere.rikkahub.data.ai.transformers

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import androidx.compose.material3.Text
import org.jetbrains.compose.resources.stringResource
import rikkahub.composeapp.generated.resources.Res
import rikkahub.composeapp.generated.resources.placeholder_battery_level
import rikkahub.composeapp.generated.resources.placeholder_char
import rikkahub.composeapp.generated.resources.placeholder_current_date
import rikkahub.composeapp.generated.resources.placeholder_current_datetime
import rikkahub.composeapp.generated.resources.placeholder_current_time
import rikkahub.composeapp.generated.resources.placeholder_device_info
import rikkahub.composeapp.generated.resources.placeholder_locale
import rikkahub.composeapp.generated.resources.placeholder_model_id
import rikkahub.composeapp.generated.resources.placeholder_model_name
import rikkahub.composeapp.generated.resources.placeholder_nickname
import rikkahub.composeapp.generated.resources.placeholder_system_version
import rikkahub.composeapp.generated.resources.placeholder_timezone
import rikkahub.composeapp.generated.resources.placeholder_user
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.Temporal
import java.util.Locale
import java.util.TimeZone

actual object DefaultPlaceholderProvider : PlaceholderProvider {
    actual override val placeholders: Map<String, PlaceholderInfo> = buildPlaceholders {
        placeholder("cur_date", { Text(stringResource(Res.string.placeholder_current_date)) }) {
            LocalDate.now().toDateString()
        }

        placeholder("cur_time", { Text(stringResource(Res.string.placeholder_current_time)) }) {
            LocalTime.now().toTimeString()
        }

        placeholder("cur_datetime", { Text(stringResource(Res.string.placeholder_current_datetime)) }) {
            LocalDateTime.now().toDateTimeString()
        }

        placeholder("model_id", { Text(stringResource(Res.string.placeholder_model_id)) }) {
            it.model.modelId
        }

        placeholder("model_name", { Text(stringResource(Res.string.placeholder_model_name)) }) {
            it.model.displayName
        }

        placeholder("locale", { Text(stringResource(Res.string.placeholder_locale)) }) {
            Locale.getDefault().displayName
        }

        placeholder("timezone", { Text(stringResource(Res.string.placeholder_timezone)) }) {
            TimeZone.getDefault().displayName
        }

        placeholder("system_version", { Text(stringResource(Res.string.placeholder_system_version)) }) {
            "Android SDK v${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})"
        }

        placeholder("device_info", { Text(stringResource(Res.string.placeholder_device_info)) }) {
            "${Build.BRAND} ${Build.MODEL}"
        }

        placeholder("battery_level", { Text(stringResource(Res.string.placeholder_battery_level)) }) {
            it.context.batteryLevel().toString()
        }

        placeholder("nickname", { Text(stringResource(Res.string.placeholder_nickname)) }) {
            it.settingsStore.settingsFlow.value.displaySetting.userNickname.ifBlank { "user" }
        }

        placeholder("char", { Text(stringResource(Res.string.placeholder_char)) }) {
            it.assistant.name.ifBlank { "assistant" }
        }

        placeholder("user", { Text(stringResource(Res.string.placeholder_user)) }) {
            it.settingsStore.settingsFlow.value.displaySetting.userNickname.ifBlank { "user" }
        }
    }

    private fun Temporal.toDateString() = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(this)

    private fun Temporal.toTimeString() = DateTimeFormatter
        .ofLocalizedTime(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(this)

    private fun Temporal.toDateTimeString() = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(this)

    private fun Context.batteryLevel(): Int {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}
