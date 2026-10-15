package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class DecimalFormatSymbolsCloneTest {
    private class Derived : DecimalFormatSymbols(ULocale("en")) { val extra = Any() }
    @Test fun runtimeSubtypeAndOwnStateAreShallowCopied() {
        val original = Derived()
        val copy = original.clone()
        assertTrue(copy is Derived)
        assertNotSame(original, copy)
        assertSame(original.extra, copy.extra)
        assertSame(original.getDigitStringsLocal(), copy.getDigitStringsLocal())
        copy.setZeroDigit('A')
        assertEquals('0', original.getZeroDigit())
        assertEquals('A', copy.getZeroDigit())
    }
}
