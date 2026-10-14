package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.ParsePosition
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class CurrencyParsingTest {
    @Test fun namesSymbolsOffsetsAndFailure() {
        val locale = ULocale("en")
        val position = ParsePosition(2).also { it.errorIndex = 7 }
        assertEquals("USD", CurrencyParsing.parse(locale, "x US DOLLAR!", 1, position))
        assertEquals(11, position.index); assertEquals(7, position.errorIndex)
        val failure = ParsePosition(0)
        assertNull(CurrencyParsing.parse(locale, "?", 0, failure)); assertEquals(0, failure.index)
        for (symbol in listOf("¥", "￥")) {
            val p = ParsePosition(0)
            assertEquals("JPY", CurrencyParsing.parse(locale, symbol, 0, p)); assertEquals(1, p.index)
            assertNull(CurrencyParsing.parse(locale, symbol, 1, ParsePosition(0)))
        }
    }
}
