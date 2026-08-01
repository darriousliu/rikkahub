package me.rerere.rikkahub.ui.pages.translator

enum class TranslationLanguage(
    val languageTag: String,
    val promptCode: String,
    val apiName: String,
) {
    SimplifiedChinese("zh-CN", "zh_CN", "Chinese"),
    English("en", "en", "English"),
    TraditionalChinese("zh-TW", "zh_TW", "Chinese"),
    Japanese("ja", "ja", "Japanese"),
    Korean("ko", "ko", "Korean"),
    French("fr", "fr", "French"),
    German("de", "de", "German"),
    Italian("it", "it", "Italian"),
    Spanish("es-ES", "es_ES", "Spanish"),
}
