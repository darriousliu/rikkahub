package me.rerere.rikkahub.ui.hooks

import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

@Composable
fun rememberAvatarShape(loading: Boolean): Shape {
    val infiniteTransition = rememberInfiniteTransition()
    val rotateAngle = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 3000,
                easing = LinearEasing
            ),
        )
    )
    // TODO: Re-enable when Material3 support is better
//    return if (loading) MaterialShapes.Cookie6Sided.toShape(rotateAngle.value.roundToInt()) else CircleShape
    return CircleShape
}
