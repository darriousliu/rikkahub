package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Share03
import me.rerere.rikkahub.platform.rememberPlatformTextSharer

@Composable
fun ShareSheet(state: ShareSheetState) {
    val textSharer = rememberPlatformTextSharer()
    val provider = state.currentProvider
    if (state.isShow && provider != null) {
        val sharedText = provider.encodeForShare()
        ModalBottomSheet(
            onDismissRequest = state::dismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("共享你的 LLM 模型", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { textSharer.share(sharedText) }) {
                        Icon(HugeIcons.Share03, contentDescription = null)
                    }
                }
                QRCode(
                    value = sharedText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
            }
        }
    }
}

@Stable
class ShareSheetState {
    private var show by mutableStateOf(false)
    val isShow: Boolean get() = show

    private var provider by mutableStateOf<ProviderSetting?>(null)
    val currentProvider: ProviderSetting? get() = provider

    fun show(provider: ProviderSetting) {
        show = true
        this.provider = provider
    }

    fun dismiss() {
        show = false
    }
}

@Composable
fun rememberShareSheetState(): ShareSheetState = remember { ShareSheetState() }
