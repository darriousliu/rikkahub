package me.rerere.common.text

import kotlin.test.Test
import kotlin.test.assertEquals

class DecimalFormatContractTest {
    @Test
    fun `fixed decimals retain half up rounding and negative zero`() {
        // The device locale determines the separator; compare with another value in that same locale.
        val separator = formatFixedDecimal(1.5, 2).removePrefix("1").removeSuffix("50")
        assertEquals("0${separator}13", formatFixedDecimal(0.125, 2))
        assertEquals("-0${separator}00", formatFixedDecimal(-0.0, 2))
        assertEquals("-0${separator}00", formatFixedDecimal((-0.004f).toDouble(), 2))
        assertEquals("0${separator}14", formatFixedDecimal(0.145f.toDouble(), 2))
    }
}
