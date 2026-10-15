package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.number.NumberFormatPatternData
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class CurrencyPluralInfoTest {
    @Test fun publicPatternsRulesCloningAndIteratorRemoval() {
        val info = CurrencyPluralInfo(ULocale("en_US"))
        assertEquals("#,##0.### ¤¤¤", info.getCurrencyPluralPattern("one"))
        assertEquals("one", info.select(PluralRules.FixedDecimal(1)))
        assertEquals("other", info.select(PluralRules.FixedDecimal(2)))
        val copy = info.clone()
        assertEquals(info, copy); assertEquals(info.hashCode(), copy.hashCode())
        assertSame(info.getLocale(), copy.getLocale()); assertSame(info.getPluralRules(), copy.getPluralRules())
        copy.setCurrencyPluralPattern("one", "custom")
        assertNotEquals(info.getCurrencyPluralPattern("one"), copy.getCurrencyPluralPattern("one"))
        copy.setCurrencyPluralPattern("other", null)
        assertEquals(0, copy.getCurrencyPluralPattern("unknown")[0].code)
        copy.setCurrencyPluralPattern(null, "nullable-key")
        assertEquals("nullable-key", copy.getCurrencyPluralPattern(null))
        copy.setCurrencyPluralPattern(null, null)
        assertFailsWith<NullPointerException> { copy.getCurrencyPluralPattern(null) }
        val iterator = copy.pluralPatternIterator(); val removed = iterator.next(); iterator.remove()
        assertFalse(copy.pluralPatternIterator().asSequence().toList().contains(removed))
        info.setPluralRules("invalid")
        assertNull(info.getPluralRules())
        assertFailsWith<NullPointerException> { info.equals(info) }
        info.setLocale(ULocale("ru"))
        assertEquals("few", info.select(PluralRules.FixedDecimal(2)))
    }
    @Test fun stylePatternsRespectAccountingAndLatinFallback() {
        val locale = ULocale("en_US@cf=account")
        assertEquals(NumberFormatPatternData.getPatternForStyle(locale, 7), NumberFormatPatternData.getPatternForStyle(locale, 1))
        assertNotEquals(NumberFormatPatternData.getPatternForStyle(locale, 9), NumberFormatPatternData.getPatternForStyle(locale, 1))
        assertEquals(NumberFormatPatternData.getPatternForStyleAndNumberingSystem(ULocale("en_US"), "latn", 0),
            NumberFormatPatternData.getPatternForStyleAndNumberingSystem(ULocale("en_US"), "missing", 0))
    }
}
