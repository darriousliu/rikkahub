package me.rerere.rikkahub.ui.pages.setting.components

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Refresh03
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.BalanceOption
import me.rerere.ai.provider.ProviderSetting
import me.rerere.common.http.isJsonExprValid
import me.rerere.rikkahub.data.datastore.DEFAULT_PROVIDERS
import me.rerere.rikkahub.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private val ApiPathRegex = Regex("""^/[^ \t\n\r]*$""")

internal fun isValidBalanceApiPath(value: String): Boolean = value.matches(ApiPathRegex)

@Composable
fun SettingProviderBalanceOption(
    provider: ProviderSetting,
    balanceOption: BalanceOption,
    modifier: Modifier = Modifier,
    onEdit: (BalanceOption) -> Unit,
) {
    var expand by remember { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.setting_provider_page_balance_info),
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    expand = !expand
                }
            ) {
                if (expand) {
                    Icon(
                        imageVector = HugeIcons.ArrowUp01,
                        contentDescription = null,
                    )
                } else {
                    Icon(
                        imageVector = HugeIcons.ArrowDown01,
                        contentDescription = null,
                    )
                }
            }
            Checkbox(
                checked = balanceOption.enabled,
                onCheckedChange = { onEdit(balanceOption.copy(enabled = it)) }
            )
        }
        AnimatedVisibility(visible = expand) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = balanceOption.apiPath,
                    onValueChange = { onEdit(balanceOption.copy(apiPath = it)) },
                    label = { Text(stringResource(Res.string.setting_provider_page_balance_api_path)) },
                    isError = !isValidBalanceApiPath(balanceOption.apiPath),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = balanceOption.resultPath,
                    onValueChange = { onEdit(balanceOption.copy(resultPath = it)) },
                    label = { Text(stringResource(Res.string.setting_provider_page_balance_json_key)) },
                    isError = !isJsonExprValid(balanceOption.resultPath),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
                IconButton(
                    onClick = {
                        val defaultProvider = DEFAULT_PROVIDERS.find { it.id == provider.id }
                        if (defaultProvider != null) {
                            onEdit(defaultProvider.balanceOption.copy())
                        } else {
                            onEdit(BalanceOption())
                        }
                    }
                ) {
                    Icon(HugeIcons.Refresh03, null)
                }
            }
        }
    }
}
