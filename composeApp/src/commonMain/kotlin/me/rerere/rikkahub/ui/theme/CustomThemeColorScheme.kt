package me.rerere.rikkahub.ui.theme

import androidx.compose.material3.ColorScheme
import dynamiccolor.ColorSpecs
import dynamiccolor.DynamicScheme
import dynamiccolor.Variant
import hct.Hct
import me.rerere.material3.toColorScheme
import palettes.TonalPalette

fun CustomTheme.generateColorScheme(dark: Boolean): ColorScheme {
    val sourceHct = Hct.fromInt(primaryColorArgb.toInt())
    val specVersion = DynamicScheme.DEFAULT_SPEC_VERSION
    val platform = DynamicScheme.DEFAULT_PLATFORM
    val contrastLevel = 0.0
    val colorSpec = ColorSpecs.get(specVersion)

    val primaryPalette = colorSpec.getPrimaryPalette(
        Variant.TONAL_SPOT, sourceHct, dark, platform, contrastLevel,
    )
    val secondaryColor = secondaryColorArgb
    val secondaryPalette = if (secondaryColor != null) {
        TonalPalette.fromInt(secondaryColor.toInt())
    } else {
        colorSpec.getSecondaryPalette(
            Variant.TONAL_SPOT, sourceHct, dark, platform, contrastLevel,
        )
    }
    val tertiaryColor = tertiaryColorArgb
    val tertiaryPalette = if (tertiaryColor != null) {
        TonalPalette.fromInt(tertiaryColor.toInt())
    } else {
        colorSpec.getTertiaryPalette(
            Variant.TONAL_SPOT, sourceHct, dark, platform, contrastLevel,
        )
    }

    val scheme = DynamicScheme(
        sourceHct,
        Variant.TONAL_SPOT,
        dark,
        contrastLevel,
        platform,
        specVersion,
        primaryPalette,
        secondaryPalette,
        tertiaryPalette,
        colorSpec.getNeutralPalette(
            Variant.TONAL_SPOT, sourceHct, dark, platform, contrastLevel,
        ),
        colorSpec.getNeutralVariantPalette(
            Variant.TONAL_SPOT,
            sourceHct,
            dark,
            platform,
            contrastLevel,
        ),
        colorSpec.getErrorPalette(
            Variant.TONAL_SPOT, sourceHct, dark, platform, contrastLevel,
        ),
    )
    return scheme.toColorScheme()
}
