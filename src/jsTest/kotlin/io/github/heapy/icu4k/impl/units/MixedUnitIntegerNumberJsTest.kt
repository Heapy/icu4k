package io.github.heapy.icu4k.impl.units

import io.github.heapy.icu4k.util.BigDecimal
import kotlin.test.*

class MixedUnitIntegerNumberJsTest {
    // Kotlin/JS numberToLong converts through a JS number and ignores the Number override.
    // Keep the source requirement visible until generic Number intrinsic dispatch is fixed.
    @Ignore
    @Test fun genericNumberConversionMustRetainLow64Bits() {
        val integer: Number = MixedUnitInteger(BigDecimal("18446744073709551616")).toNumber()
        assertEquals(0L, integer.toLong())
    }
}
