package me.rerere.rikkahub.utils

import androidx.compose.ui.text.intl.Locale
import platform.Foundation.NSLocale
import platform.Foundation.localeWithLocaleIdentifier
import platform.Foundation.localizedStringForLanguageCode

actual fun getDisplayLanguageInEnglish(locale: Locale): String {
    val nsLocale = NSLocale.localeWithLocaleIdentifier("en")
    val languageCode = locale.toLanguageTag()

    return nsLocale.localizedStringForLanguageCode(languageCode)
        ?: locale.language.replaceFirstChar { it.uppercase() }
}

actual fun getDisplayLanguage(
    locale: Locale,
    displayLocale: Locale
): String {
    val nsDisplayLocale = NSLocale.localeWithLocaleIdentifier(displayLocale.toLanguageTag())
    val languageCode = locale.toLanguageTag()

    return nsDisplayLocale.localizedStringForLanguageCode(languageCode)
        ?: locale.language.replaceFirstChar { it.uppercase() }
}
