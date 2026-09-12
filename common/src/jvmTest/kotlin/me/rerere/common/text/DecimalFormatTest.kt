package me.rerere.common.text

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class DecimalFormatTest {
    @Test
    fun `fixed decimals match the original Java formatter across locales and float boundaries`() {
        val originalLocale = Locale.getDefault()
        try {
            for (locale in listOf(Locale.US, Locale.GERMANY, Locale.CHINA)) {
                Locale.setDefault(locale)
                for (value in listOf(0.125f, -0.0f, -0.004f, 0.145f, 12.5f, Float.MAX_VALUE, Float.NaN,
                    Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
                    assertEquals(String.format("%.2f", value), formatFixedDecimal(value.toDouble(), 2), "$locale $value")
                }
            }
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
