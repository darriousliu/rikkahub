package me.rerere.rikkahub.ui.components.nav

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import me.rerere.rikkahub.ui.context.LocalNavController
import org.jetbrains.compose.resources.stringResource
import rikkahub.composeapp.generated.resources.Res
import rikkahub.composeapp.generated.resources.back

@Composable
fun BackButton(modifier: Modifier = Modifier) {
    val navController = LocalNavController.current
    IconButton(
        onClick = {
            navController.popBackStack()
        },
        modifier = modifier
    ) {
        Icon(
            Lucide.ArrowLeft,
            contentDescription = stringResource(Res.string.back)
        )
    }
}
