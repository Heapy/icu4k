package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.CurrencyUsage
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class CurrencyMetadataTest {
    @Test fun codesDatesAndRounding() {
        assertEquals("USD", CurrencyMetadata.normalizeCode("usd"))
        assertFailsWith<IllegalArgumentException> { CurrencyMetadata.normalizeCode("€UR") }
        assertEquals(840, CurrencyMetadata.numericCode("USD")); assertEquals(0, CurrencyMetadata.numericCode("ZZZ"))
        assertEquals("USD", CurrencyMetadata.regionCurrencyCode("US"))
        assertEquals(0.05, CurrencyMetadata.roundingIncrement("CHF", CurrencyUsage.CASH))
        assertEquals(0, CurrencyMetadata.fractionDigits("JPY", CurrencyUsage.STANDARD))
        assertTrue(CurrencyMetadata.isAvailable("usd", null, null))
        assertFalse(CurrencyMetadata.isAvailable("?", 1, 0))
        assertFailsWith<IllegalArgumentException> { CurrencyMetadata.isAvailable("ZZZ", 1, 0) }
        assertTrue(CurrencyMetadata.keywordValues("currency", ULocale("und"), true).isEmpty())
        assertEquals(listOf("USD"), CurrencyMetadata.availableCurrencyCodes(ULocale("en_US"), 0)?.toList())
    }
}
