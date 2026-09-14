package me.rerere.rikkahub.ui.components.ui

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.petterp.floatingx.app.appHost
import com.petterp.floatingx.compose.compose
import com.petterp.floatingx.core.FloatingX
import com.petterp.floatingx.core.FxControl
import com.petterp.floatingx.core.animation.FxAnimations
import com.petterp.floatingx.core.layout.FxAdsorb
import com.petterp.floatingx.core.layout.FxGravity

@Composable
actual fun FloatingWindow(
    tag: String,
    visibility: Boolean,
    content: @Composable () -> Unit,
) {
    val application = LocalContext.current.applicationContext as Application
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    val currentContent by rememberUpdatedState<@Composable () -> Unit> {
        MaterialTheme(colorScheme = colorScheme, typography = typography, shapes = shapes) {
            content()
        }
    }
    var window by remember { mutableStateOf<FxControl?>(null) }

    DisposableEffect(application, tag) {
        val control = FloatingX.install(tag) {
            appHost(application)
            anchor(FxGravity.BOTTOM_START, dx = 20f, dy = 20f)
            adsorb(FxAdsorb.horizontal())
            animation(FxAnimations.fade())
            compose { currentContent() }
        }
        window = control
        if (visibility) control.show() else control.hide()
        onDispose {
            control.cancel()
            window = null
        }
    }
    LaunchedEffect(visibility, window) {
        if (visibility) window?.show() else window?.hide()
    }
}
