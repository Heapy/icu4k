package io.github.heapy.icu4k.impl.number.parse

import io.github.heapy.icu4k.impl.StringSegment
import io.github.heapy.icu4k.impl.number.DecimalQuantity_DualStorageBCD
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.util.Currency
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class CurrencyAffixMatcherTest {
    @Test fun currencySpacingAndLocalNames() {
        val symbols = DecimalFormatSymbols(ULocale.ENGLISH)
        val matcher = CombinedCurrencyMatcher.getInstance(Currency.getInstance("USD"), symbols, ParsingUtils.PARSE_FLAG_NO_FOREIGN_CURRENCIES)
        val prefix = ParsedNumber()
        val prefixSegment = StringSegment("USD\u00a0", true)
        matcher.match(prefixSegment, prefix)
        assertEquals("USD", prefix.currencyCode)
        assertEquals(3, prefix.charEnd)
        assertEquals(4, prefixSegment.getOffset())
        val suffix = ParsedNumber().also { it.quantity = DecimalQuantity_DualStorageBCD(1) }
        val suffixSegment = StringSegment("\u00a0US dollars", true)
        matcher.match(suffixSegment, suffix)
        assertEquals("USD", suffix.currencyCode)
        assertEquals(suffixSegment.getOffset(), suffix.charEnd)
        assertEquals(11, suffix.charEnd)
        val partial = ParsedNumber()
        val partialSegment = StringSegment("US doll", true)
        assertTrue(matcher.match(partialSegment, partial))
        assertNull(partial.currencyCode); assertEquals(0, partialSegment.getOffset())
    }
    @Test fun affixRollbackAndWeakSpaces() {
        val factory = AffixTokenMatcherFactory().also {
            it.symbols = DecimalFormatSymbols(ULocale.ENGLISH)
            it.currency = Currency.getInstance("USD")
            it.ignorables = IgnorablesMatcher.getInstance(0)
        }
        val matcher = AffixPatternMatcher.fromAffixPattern("'a' -", factory, 0)!!
        val result = ParsedNumber(); val segment = StringSegment("a   -!", false)
        matcher.match(segment, result)
        assertEquals(5, segment.getOffset()); assertEquals(5, result.charEnd)
        assertTrue(result.flags and ParsedNumber.FLAG_NEGATIVE != 0)
        val failed = ParsedNumber(); val failedSegment = StringSegment("a  x", false)
        matcher.match(failedSegment, failed)
        assertEquals(0, failedSegment.getOffset()); assertEquals(0, failed.charEnd); assertEquals(0, failed.flags)
        val exact = AffixPatternMatcher.fromAffixPattern("'a' -", factory, ParsingUtils.PARSE_FLAG_EXACT_AFFIX)!!
        val exactSegment = StringSegment("a   -!", false)
        exact.match(exactSegment, ParsedNumber())
        assertEquals(0, exactSegment.getOffset())
        assertNull(AffixPatternMatcher.fromAffixPattern("", null, 0))
    }
}
