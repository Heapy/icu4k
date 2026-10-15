// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.text.NumberFormatField
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.test.*

class AffixUtilsTest {
    @Test fun quotesCurrenciesAndSupplementaryTokens() {
        val tokens = mutableListOf<Int>()
        AffixUtils.iterateWithConsumer("'a-'¤¤😀''+", AffixUtils.TokenConsumer { tokens.add(it) })
        assertEquals(listOf('a'.code, '-'.code, AffixUtils.TYPE_CURRENCY_DOUBLE, 0x1f600, 39, AffixUtils.TYPE_PLUS_SIGN), tokens)
        assertTrue(AffixUtils.hasCurrencySymbols("'¤'¤¤¤"))
        assertFalse(AffixUtils.hasCurrencySymbols("'¤'"))
        assertTrue(AffixUtils.containsType("+ '", AffixUtils.TYPE_PLUS_SIGN))
        assertEquals("¤x", AffixUtils.replaceType("¤¤", AffixUtils.TYPE_CURRENCY_DOUBLE, 'x'))
        assertEquals("\ud83dx", AffixUtils.replaceType("😀", 0x1f600, 'x'))
        assertEquals("'-+%'x", AffixUtils.escape("-+%x"))
        assertEquals("~", AffixUtils.escape("~")) // Source does not quote approximately signs.
        assertFailsWith<IllegalArgumentException> { AffixUtils.estimateLength("'a") }
    }
    @Test fun providerSubstitutionFieldsAndOverflow() {
        val called = mutableListOf<Int>()
        val provider = AffixUtils.SymbolProvider { type -> called.add(type); if (type == AffixUtils.TYPE_CURRENCY_SINGLE) "USD" else "😀" }
        val output = FormattedStringBuilder().apply { append("[]", null) }
        assertEquals(9, AffixUtils.unescape("'x'¤+¤¤¤¤¤¤%", output, 1, provider, NumberFormatField.MEASURE_UNIT))
        assertEquals("[xUSD😀�😀]", output.toString())
        assertEquals(listOf(-6, -2, -4), called)
        assertSame(NumberFormatField.MEASURE_UNIT, output.fieldAt(1))
        assertSame(NumberFormatField.CURRENCY, output.fieldAt(2))
        assertSame(NumberFormatField.SIGN, output.fieldAt(5))
        assertSame(NumberFormatField.CURRENCY, output.fieldAt(7))
        assertEquals(1, AffixUtils.unescapedCount("¤¤¤¤¤¤", false, null))
        assertEquals(0, AffixUtils.unescape("", null, 0, null, null))
        assertTrue(AffixUtils.containsOnlySymbolsAndIgnorables("-+% ", UnicodeSet().add(32)))
        assertFalse(AffixUtils.containsOnlySymbolsAndIgnorables("'-'", UnicodeSet()))
    }
}
