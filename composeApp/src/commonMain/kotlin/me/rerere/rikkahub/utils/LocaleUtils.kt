package me.rerere.rikkahub.utils

import androidx.compose.ui.text.intl.Locale

//private val Locales by lazy {
//    listOf(
//        Locale.SIMPLIFIED_CHINESE,
//        Locale.ENGLISH,
//        Locale.TRADITIONAL_CHINESE,
//        Locale.JAPANESE,
//        Locale.KOREAN,
//        Locale.FRENCH,
//        Locale.GERMAN,
//        Locale.ITALIAN,
//    )
//}
val Locale.Companion.SIMPLIFIED_CHINESE by lazy {
    Locale("zh-CN")
}

val Locale.Companion.ENGLISH by lazy {
    Locale("en-US")
}

val Locale.Companion.TRADITIONAL_CHINESE by lazy {
    Locale("zh-TW")
}

val Locale.Companion.JAPANESE by lazy {
    Locale("ja-JP")
}

val Locale.Companion.KOREAN by lazy {
    Locale("ko-KR")
}

val Locale.Companion.FRENCH by lazy {
    Locale("fr-FR")
}

val Locale.Companion.GERMAN by lazy {
    Locale("de-DE")
}

val Locale.Companion.ITALIAN by lazy {
    Locale("it-IT")
}

val Locale.Companion.SPANISH by lazy {
    Locale("es-ES")
}

fun Locale.getDisplayLanguageInEnglish(): String {
    return getDisplayLanguageInEnglish(this)
}

fun Locale.getDisplayLanguage(locale: Locale = this): String {
    return getDisplayLanguage(this, locale)
}

/**
 * 获取指定语言在英文环境下的显示名称
 * @param locale Compose Locale对象
 * @return 英文语言名称，如 "Chinese"、"French"
 */
expect fun getDisplayLanguageInEnglish(locale: Locale): String

/**
 * 获取指定语言在指定显示语言环境下的名称
 * @param locale 目标语言
 * @param displayLocale 显示语言环境
 * @return 本地化的语言名称
 */
expect fun getDisplayLanguage(locale: Locale, displayLocale: Locale): String
