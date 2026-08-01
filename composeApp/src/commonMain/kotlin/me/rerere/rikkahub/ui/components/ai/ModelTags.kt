package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Image03
import me.rerere.hugeicons.stroke.Text
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.generated.resources.Res
import me.rerere.rikkahub.generated.resources.deepthink
import me.rerere.rikkahub.generated.resources.setting_provider_page_chat_model
import me.rerere.rikkahub.generated.resources.setting_provider_page_embedding_model
import me.rerere.rikkahub.generated.resources.setting_provider_page_image_model
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.utils.toDp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ModelTypeTag(model: Model) {
    Tag(type = TagType.INFO) {
        Text(
            text = stringResource(
                when (model.type) {
                    ModelType.CHAT -> Res.string.setting_provider_page_chat_model
                    ModelType.EMBEDDING -> Res.string.setting_provider_page_embedding_model
                    ModelType.IMAGE -> Res.string.setting_provider_page_image_model
                },
            ),
        )
    }
}

@Composable
fun ModelModalityTag(model: Model) {
    Tag(type = TagType.SUCCESS) {
        model.inputModalities.fastForEach { modality ->
            Icon(
                imageVector = when (modality) {
                    Modality.TEXT -> HugeIcons.Text
                    Modality.IMAGE -> HugeIcons.Image03
                },
                contentDescription = null,
                modifier = Modifier
                    .size(LocalTextStyle.current.lineHeight.toDp())
                    .padding(1.dp),
            )
        }
        Icon(
            imageVector = HugeIcons.ArrowRight01,
            contentDescription = null,
            modifier = Modifier.size(LocalTextStyle.current.lineHeight.toDp()),
        )
        model.outputModalities.fastForEach { modality ->
            Icon(
                imageVector = when (modality) {
                    Modality.TEXT -> HugeIcons.Text
                    Modality.IMAGE -> HugeIcons.Image03
                },
                contentDescription = null,
                modifier = Modifier
                    .size(LocalTextStyle.current.lineHeight.toDp())
                    .padding(1.dp),
            )
        }
    }
}

@Composable
fun ModelAbilityTag(model: Model) {
    model.abilities.fastForEach { ability ->
        when (ability) {
            ModelAbility.TOOL -> Tag(type = TagType.WARNING) {
                Icon(
                    imageVector = HugeIcons.Tools,
                    contentDescription = null,
                    modifier = Modifier.size(LocalTextStyle.current.lineHeight.toDp()),
                )
            }

            ModelAbility.REASONING -> Tag(type = TagType.INFO) {
                Icon(
                    painter = painterResource(Res.drawable.deepthink),
                    contentDescription = null,
                    modifier = Modifier.size(LocalTextStyle.current.lineHeight.toDp()),
                )
            }
        }
    }
}
