package me.rerere.search

import java.util.Locale

internal actual fun currentSearchLocale(): SearchLocale = Locale.getDefault().let { locale ->
    SearchLocale(language = locale.language, country = locale.country)
}
