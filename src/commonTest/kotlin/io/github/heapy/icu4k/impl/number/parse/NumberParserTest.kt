package io.github.heapy.icu4k.impl.number.parse

import io.github.heapy.icu4k.impl.number.PatternStringParser
import io.github.heapy.icu4k.impl.number.DecimalFormatProperties.ParseMode
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.text.ParsePosition
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class NumberParserTest {
    @Test fun propertiesAndPairedAffixes() {
        val symbols = DecimalFormatSymbols(ULocale.ENGLISH)
        val props = PatternStringParser.parseToProperties("'abc'0.00'xyz';('abc'0.00'xyz')").setParseMode(ParseMode.STRICT)
        val parser = NumberParserImpl.createParserFromProperties(props, symbols, false)
        for (greedy in listOf(false, true)) {
            val result = ParsedNumber()
            parser.parse("(abc12.50xyz)", greedy, result)
            assertTrue(result.success()); assertEquals(13, result.charEnd)
            assertEquals(-12.5, result.getNumber().toDouble())
            val failed = ParsedNumber()
            parser.parse("abc12.50", greedy, failed)
            assertFalse(failed.success())
        }
        val percent = PatternStringParser.parseToProperties("0%")
        val position = ParsePosition(5)
        assertEquals(0.125, NumberParserImpl.parseStatic("12.5%", position, percent, symbols)!!.toDouble())
        assertEquals(5, position.index)
        val currency = NumberParserImpl.parseStaticCurrency("USD12", position, PatternStringParser.parseToProperties("0"), symbols)!!
        assertEquals("USD", currency.getCurrency().getCurrencyCode()); assertEquals(12L, currency.getNumber().toLong())
    }
    @Test fun recursiveLimitAndStartOffset() {
        fun parser(flags: Int): NumberParserImpl = NumberParserImpl(flags).also { it.addMatcher(CodePointMatcher.getInstance(97)); it.freeze() }
        val input = "a".repeat(110)
        val greedy = ParsedNumber(); parser(0).parse(input, true, greedy)
        assertEquals(110, greedy.charEnd)
        val limited = ParsedNumber(); parser(0).parse(input, false, limited)
        assertEquals(100, limited.charEnd)
        val unlimited = ParsedNumber(); parser(ParsingUtils.PARSE_FLAG_ALLOW_INFINITE_RECURSION).parse(input, false, unlimited)
        assertEquals(110, unlimited.charEnd)
        val start = ParsedNumber()
        NumberParserImpl.createDefaultParserForLocale(ULocale.ENGLISH).parse("xx-12.5!", 2, false, start)
        assertTrue(start.success()); assertEquals(7, start.charEnd); assertEquals(-12.5, start.getNumber().toDouble())
    }
}
