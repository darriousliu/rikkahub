package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.reactivecircus.cache4k.Cache
import kotlin.time.Duration.Companion.minutes
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.MoneyBag02
import me.rerere.rikkahub.utils.toDp
import org.koin.compose.koinInject

private val providerBalanceCache = Cache.Builder<String, String>()
    .expireAfterWrite(2.minutes)
    .build()

@Composable
fun ProviderBalanceText(
    providerSetting: ProviderSetting,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    if (!providerSetting.balanceOption.enabled || providerSetting !is ProviderSetting.OpenAI) {
        return
    }

    val providerManager = koinInject<ProviderManager>()
    val balance = produceState(initialValue = "~", providerSetting.id, providerSetting.balanceOption) {
        val cacheKey = "${providerSetting.id},${providerSetting.balanceOption.hashCode()}"
        value = providerBalanceCache.get(cacheKey) ?: runCatching {
            providerManager.getProviderByType(providerSetting).getBalance(providerSetting)
        }.onSuccess {
            providerBalanceCache.put(cacheKey, it)
        }.getOrElse {
            "Error: ${it.message}"
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = HugeIcons.MoneyBag02,
            contentDescription = null,
            modifier = Modifier.size(style.fontSize.toDp()),
            tint = color.takeOrElse { LocalContentColor.current },
        )
        Text(
            text = balance.value,
            style = style,
            maxLines = 1,
            color = color,
        )
    }
}
