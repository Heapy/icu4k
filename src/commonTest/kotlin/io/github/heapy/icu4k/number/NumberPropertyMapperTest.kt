package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.number.*
import io.github.heapy.icu4k.text.CompactStyle
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.util.Currency
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class NumberPropertyMapperTest {
    @Test fun scientificRoundingAndExportRemainSeparate() {
        val properties = PatternStringParser.parseToProperties("0.00E0")
        val exported = DecimalFormatProperties()
        val symbols = DecimalFormatSymbols(ULocale("en_US"))
        val macros = NumberPropertyMapper.oldToNew(properties, symbols, exported)
        assertSame(symbols, macros.symbols)
        assertTrue(macros.notation is ScientificNotation)
        val rounding = macros.precision as Precision.SignificantRounderImpl
        assertEquals(3, rounding.minSig); assertEquals(3, rounding.maxSig)
        assertEquals(2, exported.getMinimumFractionDigits()); assertEquals(2, exported.getMaximumFractionDigits())
        assertEquals(-1, exported.getMinimumSignificantDigits())
    }
    @Test fun currencyOverridesAndCompactPrecedence() {
        val properties = PatternStringParser.parseToProperties("¤0.00E0").setCompactStyle(CompactStyle.LONG)
        val symbols = DecimalFormatSymbols(ULocale("en_US")).apply { setCurrencySymbol("custom") }
        val exported = DecimalFormatProperties()
        val macros = NumberPropertyMapper.oldToNew(properties, symbols, exported)
        assertSame(Notation.compactLong(), macros.notation)
        assertTrue(macros.unit is CustomSymbolCurrency)
        assertEquals("custom", (macros.unit as Currency).getSymbol(ULocale("en_US")))
        assertEquals("USD", exported.getCurrency()!!.getCurrencyCode())
    }
}
