package io.github.heapy.icu4k.impl.number

import kotlin.test.*

class FixedDecimalCoreTest {
    @Test fun sampleExponentParsingRetainsVisibleZerosAndMarkerPrecedence() {
        val q = DecimalQuantityCore.fromExponentString("1.200c3")
        assertEquals("1.200c3", q.toExponentString())
        assertEquals("1200.000", q.toPlainString())
        assertEquals("1.200c3", DecimalQuantityCore.fromExponentString("1.200E3").toExponentString())
        assertEquals("1.20c3", DecimalQuantityCore.fromExponentString("١.٢٠c٣").toExponentString())
        assertEquals("0.000c2", DecimalQuantityCore.fromExponentString("0.000c2").toExponentString())
    }
    @Test fun fixedDecimalMetadataAndSourceSignConventions() {
        val d = FixedDecimalCore(-1.2, 3, 200)
        assertEquals(1.2, d.getSource())
        assertEquals(200L, d.getDecimalDigits())
        assertEquals(2L, d.getDecimalDigitsWithoutTrailingZeros())
        assertEquals(1, d.getVisibleDecimalDigitCountWithoutTrailingZeros())
        assertEquals("1.200", d.toString())
        assertEquals(-1.2, d.toDouble())
        assertEquals(1.2f, d.toFloat())
        assertEquals(1L, d.toLong())
        assertEquals(d, FixedDecimalCore(1.2, 3, 200))
        assertEquals("1.01", FixedDecimalFormatting.plain(1.005, 2))
        assertEquals("-0.000000000000000e+00", FixedDecimalFormatting.scientific(-0.0))
        assertEquals("-1.250000000000000e+00", FixedDecimalFormatting.scientific(-1.25))
        assertEquals("NaN", FixedDecimalFormatting.scientific(Double.NaN))
    }
}
