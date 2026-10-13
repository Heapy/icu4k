package io.github.heapy.icu4k.impl

import kotlin.test.*

class ICUNumberParsingTest {
    @Test fun decimalHexRoundingAndSyntax() {
        assertEquals(0.5, ICUNumberParsing.parseDouble(" 0x1p-1D "))
        assertEquals(1.0, ICUNumberParsing.parseDouble("1f"))
        assertEquals(1.0, ICUNumberParsing.parseDouble("0x1.00000000000008p0"))
        assertEquals(Double.fromBits(0x3ff0000000000002L), ICUNumberParsing.parseDouble("0x1.00000000000018p0"))
        assertEquals(Double.MIN_VALUE, ICUNumberParsing.parseDouble("0x1p-1074"))
        assertEquals(0L, ICUNumberParsing.parseDouble("0x1p-1075").toBits())
        assertEquals(Double.MIN_VALUE, ICUNumberParsing.parseDouble("0x1.1p-1075"))
        assertEquals(Long.MIN_VALUE, ICUNumberParsing.parseDouble("-0x0p999999999999").toBits())
        assertEquals(Double.POSITIVE_INFINITY, ICUNumberParsing.parseDouble("0x1p1024"))
        assertTrue(ICUNumberParsing.parseDouble("-NaN").isNaN())
        for (text in listOf("", "1x", "0x1", "nan", "1_0", "0x1p", "١", "\u00a01")) assertFailsWith<NumberFormatException> { ICUNumberParsing.parseDouble(text) }
    }
}
