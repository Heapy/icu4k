// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.*
import io.github.heapy.icu4k.math.BigDecimal as IcuDecimal
import io.github.heapy.icu4k.math.MathContext as IcuContext
import kotlin.test.*

class DecimalFormatTest {
    @Test fun publicFormattingParsingPatternsAndCopies() {
        val formatter = DecimalFormat("#,##0.00", DecimalFormatSymbols(ULocale("en_US")))
        val asBase: NumberFormat = formatter
        assertEquals("1,234.50", asBase.format(BigDecimal("1234.5")))
        assertEquals("18,446,744,073,709,551,617.00", formatter.format(BigInteger("18446744073709551617")))
        assertEquals("1.25", formatter.format(IcuDecimal("1.25")))
        val position = UFieldPosition(1); val output = StringBuilder("x:")
        assertSame(output, formatter.format(1234.5, output, position))
        assertEquals(8, position.getBeginIndex()); assertEquals(10, position.getEndIndex())
        assertEquals(2, position.getCountVisibleFractionDigits())
        val number = formatter.parse("1234.50")
        assertTrue(number is IcuDecimal); assertEquals("1234.5", number.toString())
        val snapshot = formatter.toNumberFormatter()
        formatter.setMaximumFractionDigits(1); formatter.setMinimumFractionDigits(1)
        assertEquals("1.2", formatter.format(1.2345))
        assertEquals("1.23", snapshot.format(1.2345).toString())
        val copy = formatter.clone(); assertEquals(formatter, copy); assertNotSame(formatter, copy)
        copy.applyPattern("0.000")
        assertEquals("1.234", copy.format(1.2345)); assertEquals("1.2", formatter.format(1.2345))
        val symbols = formatter.getDecimalFormatSymbols(); symbols.setDecimalSeparator('!')
        assertEquals("1.2", formatter.format(1.2345))
        formatter.setDecimalFormatSymbols(symbols)
        assertEquals("1!2", formatter.format(1.2345))
        formatter.setParseBigDecimal(true)
        assertTrue(formatter.parse("12") is IcuDecimal)
        assertEquals(1000, formatter.getParseMaxDigits())
        formatter.setParseMaxDigits(-1)
        assertEquals(1000, formatter.getParseMaxDigits())
    }
    @Test fun currencyAndCallbacksDoNotChangeConfiguredCurrency() {
        val f = DecimalFormat("¤0.00", DecimalFormatSymbols(ULocale("en_US")))
        val saved = f.getCurrency()
        assertEquals("€12.50", f.format(CurrencyAmount(12.5, Currency.getInstance("EUR"))))
        assertSame(saved, f.getCurrency())
        val parsed = f.parseCurrency("EUR 12.50", ParsePosition(0))!!
        assertEquals("EUR", parsed.getCurrency().getCurrencyCode())
        assertEquals("12.5", parsed.getNumber().toString())
        f.setProperties { it.setPositivePrefix("literal"); it.setMinimumFractionDigits(3) }
        assertTrue(f.format(1L).startsWith("literal"))
        f.applyPattern("0.0")
        assertEquals("1.0", f.format(1L))
        f.setMathContextICU(IcuContext(4, IcuContext.ENGINEERING, false, IcuContext.ROUND_DOWN))
        assertEquals(IcuContext.ENGINEERING, f.clone().getMathContextICU().getForm())
    }
    @Test fun localeChangesInCallbacksAreVisibleToRefresh() {
        val f = DecimalFormat("0.00", DecimalFormatSymbols(ULocale("en_US")))
        f.setLocale(ULocale("en_US"), ULocale("en_US"))
        f.setProperties { f.setLocale(ULocale("fr_FR"), ULocale("fr_FR")); it.setMaximumFractionDigits(3) }
        assertEquals("fr_FR", f.toNumberFormatter().resolve().loc!!.getName())
        val copy = f.clone()
        copy.setLocale(ULocale("de"), ULocale("de")); copy.setMaximumFractionDigits(4)
        assertEquals("de", copy.toNumberFormatter().resolve().loc!!.getName())
        assertEquals("fr_FR", f.toNumberFormatter().resolve().loc!!.getName())
    }
    private class VirtualCalls : DecimalFormat("0.00", DecimalFormatSymbols(ULocale("en_US"))) {
        val calls = arrayListOf<String>()
        override fun applyPattern(pattern: String?) { calls.add("pattern:$pattern"); super.applyPattern(pattern) }
        override fun toPattern(): String { calls.add("toPattern"); return super.toPattern() }
        override fun setRoundingIncrement(increment: BigDecimal?) { calls.add("increment:$increment"); super.setRoundingIncrement(increment) }
        override fun setMathContext(value: MathContext?) { calls.add("setContext:$value"); super.setMathContext(value) }
        override fun getMathContext(): MathContext? { calls.add("getContext"); return super.getMathContext() }
    }
    @Test fun virtualCrossCallsRemainVisible() {
        val f = VirtualCalls()
        f.applyLocalizedPattern("0.000")
        assertEquals(listOf("pattern:0.000"), f.calls); f.calls.clear()
        f.toLocalizedPattern()
        assertEquals(listOf("toPattern"), f.calls); f.calls.clear()
        f.setRoundingIncrement(0.05)
        assertEquals(listOf("increment:0.05"), f.calls); f.calls.clear()
        f.setRoundingIncrement(IcuDecimal("0.10"))
        assertEquals(listOf("increment:0.10"), f.calls); f.calls.clear()
        f.setMathContextICU(IcuContext(4))
        assertTrue(f.calls.single().startsWith("setContext:")); f.calls.clear()
        f.getMathContextICU()
        assertEquals(listOf("getContext"), f.calls)
    }
}
