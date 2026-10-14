@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.CurrencyData
import io.github.heapy.icu4k.impl.ICUCurrencyDisplayInfoProvider
import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class CurrencyDisplayNamesTest {
    @Test fun localizedNamesVariantsAndNoSubstitution() {
        assertTrue(CurrencyDisplayNames.hasData())
        val en = CurrencyDisplayNames.getInstance(ULocale.ENGLISH)
        assertEquals("US Dollar", en.getName("USD")); assertEquals("$", en.getSymbol("USD"))
        assertEquals("US dollars", en.getPluralName("USD", "other"))
        assertEquals("bogus", en.getName("bogus"))
        val fr = CurrencyDisplayNames.getInstance(ULocale.FRENCH)
        assertEquals("dollar des États-Unis", fr.getName("USD"))
        assertEquals("$", fr.getNarrowSymbol("USD"))
        val strict = CurrencyDisplayNames.getInstance(ULocale.ENGLISH, true)!!
        assertNull(strict.getName("bogus"))
        assertNull(CurrencyDisplayNames.getInstance(ULocale("zzzz"), true))
        assertEquals("USD", en.symbolMap()["$"])
        assertEquals("USD", en.nameMap()["US dollars"])
    }
    @Test fun spacingArraysAndSourceMutableParsingMaps() {
        val provider = ICUCurrencyDisplayInfoProvider()
        val info = provider.getInstance(ULocale.ENGLISH, true)!!
        val map = info.symbolMap() as MutableMap<String, String>
        map["custom"] = "ZZZ"; assertEquals("ZZZ", info.symbolMap()["custom"])
        val spacing = CurrencyData.CurrencySpacingInfo()
        spacing.setSymbolIfNull(CurrencyData.CurrencySpacingInfo.SpacingType.BEFORE, CurrencyData.CurrencySpacingInfo.SpacingPattern.INSERT_BETWEEN, "a")
        spacing.setSymbolIfNull(CurrencyData.CurrencySpacingInfo.SpacingType.BEFORE, CurrencyData.CurrencySpacingInfo.SpacingPattern.INSERT_BETWEEN, "b")
        assertEquals("a", spacing.getBeforeSymbols()[2])
        spacing.getBeforeSymbols()[2] = "changed"; assertEquals("changed", spacing.getBeforeSymbols()[2])
        assertFalse(CurrencyData.CurrencySpacingInfo.DEFAULT.hasBeforeCurrency)
        assertEquals(StandardPlural.EQ_0, StandardPlural.fromString("0"))
        assertEquals(StandardPlural.EQ_1, StandardPlural.fromString("=1"))
        assertEquals(StandardPlural.OTHER, StandardPlural.orOtherFromString("invalid"))
        assertFailsWith<NullPointerException> { StandardPlural.orNullFromString(null) }
    }
}
