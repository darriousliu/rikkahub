package me.rerere.rikkahub.utils

import androidx.compose.ui.text.intl.Locale

actual fun getDisplayLanguageInEnglish(locale: Locale): String {
    return locale.platformLocale.getDisplayLanguage(java.util.Locale.ENGLISH)
}

actual fun getDisplayLanguage(
    locale: Locale,
    displayLocale: Locale
): String {
    return locale.platformLocale.getDisplayLanguage(displayLocale.platformLocale)
}
