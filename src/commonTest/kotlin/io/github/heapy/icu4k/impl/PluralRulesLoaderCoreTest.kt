// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.impl.PluralRulesLoaderCore.PluralType
import io.github.heapy.icu4k.impl.number.FixedDecimalCore
import io.github.heapy.icu4k.impl.number.range.StandardPluralRanges
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class PluralRulesLoaderCoreTest {
    private val loader = PluralRulesLoaderCore.loader
    @Test fun localeFallbackCardinalAndOrdinalRules() {
        fun select(id: String, type: PluralType, n: Double): String {
            val number = FixedDecimalCore(n)
            return loader.forLocale(ULocale(id), type).rules.select(number::getPluralOperand)
        }
        assertEquals("one", select("en_US", PluralType.CARDINAL, 1.0))
        assertEquals("other", select("en_US", PluralType.CARDINAL, 2.0))
        assertEquals("two", select("en_US", PluralType.ORDINAL, 2.0))
        assertEquals("few", select("ru_RU", PluralType.CARDINAL, 23.0))
        assertEquals("other", select("zz_ZZ", PluralType.CARDINAL, 1.0))
        assertNull(loader.getOrCreateRulesForLocale(ULocale("zz_ZZ"), PluralType.CARDINAL))
        assertSame(loader.forLocale(ULocale("en"), PluralType.CARDINAL), loader.forLocale(ULocale("en_US@numbers=arab"), PluralType.CARDINAL))
    }
    @Test fun equivalentLocalesAndAvailability() {
        val available = booleanArrayOf(true, true)
        val root = loader.getFunctionalEquivalent(ULocale("zz_ZZ"), available)
        assertEquals(ULocale.ROOT, root)
        assertContentEquals(booleanArrayOf(false, true), available)
        loader.getFunctionalEquivalent(ULocale("en"), available)
        assertTrue(available[0])
        loader.getFunctionalEquivalent(ULocale("en_US"), available)
        assertFalse(available[0])
        assertTrue(loader.getAvailableULocales().size > 200)
        assertFalse(loader.hasOverride(ULocale("en")))
    }
    @Test fun rangeDataAndUnknownLanguages() {
        assertSame(StandardPluralRanges.DEFAULT, StandardPluralRanges.forSet(null))
        assertSame(StandardPluralRanges.DEFAULT, StandardPluralRanges.forLocale(ULocale("zz")))
        assertEquals(StandardPlural.OTHER, StandardPluralRanges.DEFAULT.resolve(StandardPlural.ONE, StandardPlural.ONE))
        assertEquals(StandardPlural.OTHER, StandardPluralRanges.forLocale(ULocale("en_US")).resolve(StandardPlural.ONE, StandardPlural.OTHER))
        assertEquals(StandardPlural.ONE, StandardPluralRanges.forLocale(ULocale("fr_CA")).resolve(StandardPlural.ONE, StandardPlural.ONE))
        assertEquals(StandardPlural.FEW, StandardPluralRanges.forLocale(ULocale("ru_RU")).resolve(StandardPlural.ONE, StandardPlural.FEW))
        assertNotNull(StandardPluralRanges.getSetForLocale(ULocale("en")))
    }
}
