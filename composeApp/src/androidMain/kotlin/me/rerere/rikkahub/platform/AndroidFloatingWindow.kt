package me.rerere.rikkahub.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import com.petterp.floatingx.FloatingX
import com.petterp.floatingx.assist.FxGravity
import com.petterp.floatingx.assist.helper.FxAppHelper
import com.petterp.floatingx.listener.control.IFxAppControl

public actual fun createFloatingWindowHost(): FloatingWindowHost = AndroidFloatingWindowHost()

private class AndroidFloatingWindowHost : FloatingWindowHost {
    @Composable
    override fun Content(
        tag: String,
        visible: Boolean,
        content: @Composable () -> Unit,
    ) {
        val context = LocalContext.current
        val currentContent by rememberUpdatedState(content)
        var window by remember { mutableStateOf<IFxAppControl?>(null) }

        LaunchedEffect(visible) {
            if (visible) window?.show() else window?.hide()
        }

        DisposableEffect(context, tag) {
            val helper = FxAppHelper.builder()
                .setTag(tag)
                .setContext(context)
                .setGravity(FxGravity.LEFT_OR_BOTTOM)
                .setOffsetXY(20f, -20f)
                .setEnableAnimation(true)
                .setLayoutView(
                    ComposeView(context).apply {
                        setContent { currentContent() }
                    },
                )
                .build()
            window = FloatingX.install(helper)
            if (visible) window?.show() else window?.hide()
            onDispose {
                window?.cancel()
                window = null
            }
        }
    }
}
