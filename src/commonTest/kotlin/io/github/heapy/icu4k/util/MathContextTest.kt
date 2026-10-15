// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.number.DecimalQuantityCore
import io.github.heapy.icu4k.impl.units.UnitDecimal
import kotlin.test.*

class MathContextTest {
    @Test fun constructorsConstantsParsingAndEquality() {
        assertEquals(0, MathContexts.UNLIMITED.getPrecision())
        assertEquals(RoundingMode.HALF_UP, MathContexts.UNLIMITED.getRoundingMode())
        assertEquals(listOf(7, 16, 34), listOf(MathContexts.DECIMAL32, MathContexts.DECIMAL64, MathContexts.DECIMAL128).map { it.getPrecision() })
        val a = MathContext(7, RoundingMode.HALF_EVEN)
        assertEquals(MathContexts.DECIMAL32, a)
        assertEquals(a.hashCode(), MathContexts.DECIMAL32.hashCode())
        assertEquals(a, MathContext(a.toString()))
        assertEquals("precision=7 roundingMode=HALF_EVEN", a.toString())
        assertEquals(2, MathContext("precision=٢ roundingMode=DOWN").getPrecision())
        assertEquals(RoundingMode.HALF_UP, MathContext(3).getRoundingMode())
        assertFailsWith<IllegalArgumentException> { MathContext(-1, null) }
        assertFailsWith<NullPointerException> { MathContext(0, null) }
        assertFailsWith<IllegalArgumentException> { MathContext("precision=-1 roundingMode=DOWN") }
        assertFailsWith<IllegalArgumentException> { MathContext("precision=1  roundingMode=DOWN") }
        assertFailsWith<NullPointerException> { MathContext(null as String?) }
        for (i in 0..7) assertEquals(RoundingMode.entries[i], RoundingModes.valueOf(i))
        assertFailsWith<IllegalArgumentException> { RoundingModes.valueOf(8) }
    }
    @Test fun typedContextsDriveQuantityRounding() {
        val value = DecimalQuantityCore(UnitDecimal("12345.67"))
        value.roundToMagnitude(-2, MathContext(3, RoundingMode.HALF_UP))
        assertEquals("12300", value.toPlainString())
        val nickel = DecimalQuantityCore(UnitDecimal("1.23"))
        nickel.roundToNickel(-2, MathContexts.UNLIMITED)
        assertEquals("1.25", nickel.toPlainString())
        assertFailsWith<NullPointerException> { DecimalQuantityCore().roundToMagnitude(0, null) }
    }
}
