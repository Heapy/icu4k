// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*
import io.github.heapy.icu4k.math.MathContext as IcuContext
import io.github.heapy.icu4k.math.BigDecimal as IcuDecimal
import kotlin.test.*

class DecimalFormatSettingsTest {
    private fun core() = DecimalFormatCore.create("#,##0.00", DecimalFormatSymbols(ULocale("en_US")))
    private fun format(core: DecimalFormatCore, value: Double = 1.23456) = core.format(value, StringBuilder(), FormatFieldPosition(0)).toString()
    @Test fun roundingOverloadsRetainSourceRefreshAndNoOpPolicies() {
        val core = core()
        core.setRoundingIncrement(BigDecimal("0.05"))
        val formatter = core.toNumberFormatter(); val parser = core.getParser()
        core.setRoundingIncrement(BigDecimals.ZERO)
        assertSame(formatter, core.toNumberFormatter()); assertSame(parser, core.getParser())
        assertEquals(Int.MAX_VALUE, core.properties.getMaximumFractionDigits())
        assertEquals(BigDecimal("0.05"), core.getRoundingIncrement())
        assertEquals("1.25", format(core))
        core.setRoundingIncrement(0.0)
        assertNotSame(formatter, core.toNumberFormatter()); assertNotSame(parser, core.getParser())
        assertNull(core.getRoundingIncrement())
        assertEquals("1.23456", format(core))
        val current = core.toNumberFormatter(); val currentParser = core.getParser()
        core.setParseMaxDigits(-1)
        assertEquals(1000, core.getParseMaxDigits())
        assertSame(current, core.toNumberFormatter()); assertSame(currentParser, core.getParser())
        core.setRoundingIncrement(IcuDecimal("0.10"))
        assertEquals("1.20", format(core))
    }
    @Test fun digitCouplingAndSignificanceDefaults() {
        val core = core()
        core.setMaximumIntegerDigits(3); core.setMinimumIntegerDigits(5)
        assertEquals(5, core.getMinimumIntegerDigits()); assertEquals(5, core.getMaximumIntegerDigits())
        core.setMaximumIntegerDigits(2)
        assertEquals(2, core.getMinimumIntegerDigits()); assertEquals(2, core.getMaximumIntegerDigits())
        core.setMinimumFractionDigits(4)
        assertEquals(4, core.getMinimumFractionDigits()); assertEquals(4, core.getMaximumFractionDigits())
        core.setMaximumFractionDigits(1)
        assertEquals(1, core.getMinimumFractionDigits()); assertEquals(1, core.getMaximumFractionDigits())
        val formatter = core.toNumberFormatter()
        core.setSignificantDigitsUsed(false)
        assertSame(formatter, core.toNumberFormatter())
        core.setSignificantDigitsUsed(true)
        assertTrue(core.areSignificantDigitsUsed())
        assertEquals(1, core.getMinimumSignificantDigits()); assertEquals(6, core.getMaximumSignificantDigits())
        val next = core.toNumberFormatter()
        core.setSignificantDigitsUsed(true)
        assertSame(next, core.toNumberFormatter())
        core.setMinimumSignificantDigits(9)
        assertEquals(9, core.getMaximumSignificantDigits())
        core.setSignificantDigitsUsed(false)
        assertFalse(core.areSignificantDigitsUsed())
    }
    @Test fun multipliersContextsAndOwnedCurrencyInfo() {
        val core = core()
        core.setMultiplier(100)
        assertEquals(2, core.properties.getMagnitudeMultiplier()); assertNull(core.properties.getMultiplier())
        assertEquals(100, core.getMultiplier())
        core.setMultiplier(-7)
        assertEquals(0, core.properties.getMagnitudeMultiplier()); assertEquals(BigDecimal("-7"), core.properties.getMultiplier())
        assertEquals(-7, core.getMultiplier())
        assertEquals("Multiplier must be nonzero.", assertFailsWith<IllegalArgumentException> { core.setMultiplier(0) }.message)
        val published = core.toNumberFormatter(); val parser = core.getParser()
        assertFailsWith<ArithmeticException> { core.setMathContextICU(IcuContext(4, IcuContext.ENGINEERING, true, IcuContext.ROUND_UP)) }
        assertSame(published, core.toNumberFormatter()); assertSame(parser, core.getParser())
        assertEquals(IcuContext.ENGINEERING, core.getMathContextICU().getForm())
        core.setMultiplier(1)
        core.setMathContextICU(IcuContext(4, IcuContext.ENGINEERING, true, IcuContext.ROUND_UP))
        val context = core.getMathContextICU()
        assertEquals(IcuContext.ENGINEERING, context.getForm()); assertEquals(IcuContext.ROUND_UNNECESSARY, context.getRoundingMode())
        assertFalse(context.getLostDigits())
        core.setMathContext(MathContext(6, RoundingMode.DOWN))
        assertEquals(IcuContext.ENGINEERING, core.copy().getMathContextICU().getForm())
        assertEquals(6, core.getMathContextICU().getDigits())
        val info = CurrencyPluralInfo(ULocale("en"))
        core.setCurrencyPluralInfo(info)
        assertNotSame(info, core.getCurrencyPluralInfo())
        val stored = core.getCurrencyPluralInfo()!!
        info.setCurrencyPluralPattern("other", "original changed")
        assertNotEquals("original changed", stored.getCurrencyPluralPattern("other"))
        assertSame(stored, core.copy().getCurrencyPluralInfo())
        core.setCurrency(Currency.getInstance("EUR"))
        assertEquals("EUR", core.getCurrency()!!.getCurrencyCode())
        assertEquals("€", core.getDecimalFormatSymbols().getCurrencySymbol())
        core.setCurrency(null) // Keeps monetary symbols, while clearing the explicit property.
        assertNull(core.properties.getCurrency())
        assertEquals("€", core.getDecimalFormatSymbols().getCurrencySymbol())
    }
    @Test fun parsingFlagsAffixesPaddingAndScientificSettings() {
        val core = core()
        core.setPositivePrefix("plus"); core.setNegativeSuffix("minus")
        assertEquals("plus", core.getPositivePrefix()); assertEquals("minus", core.getNegativeSuffix())
        assertFailsWith<NullPointerException> { core.setPositiveSuffix(null) }
        core.setGroupingUsed(false); core.setGroupingSize(-1); core.setSecondaryGroupingSize(-1)
        assertFalse(core.isGroupingUsed()); assertEquals(0, core.getGroupingSize()); assertEquals(0, core.getSecondaryGroupingSize())
        core.setMinimumGroupingDigits(DecimalFormatCore.MINIMUM_GROUPING_DIGITS_MIN2)
        assertEquals(1, core.getMinimumGroupingDigits())
        core.setPadCharacter('x'); core.setPadPosition(DecimalFormatCore.PAD_AFTER_PREFIX)
        assertEquals('x', core.getPadCharacter()); assertEquals(1, core.getPadPosition())
        assertFailsWith<IllegalArgumentException> { core.setPadPosition(-1) }
        core.setScientificNotation(true); core.setMinimumExponentDigits(3); core.setExponentSignAlwaysShown(true)
        assertTrue(core.isScientificNotation()); assertEquals(3.toByte(), core.getMinimumExponentDigits()); assertTrue(core.isExponentSignAlwaysShown())
        core.setScientificNotation(false)
        assertFalse(core.isScientificNotation()); assertEquals((-1).toByte(), core.getMinimumExponentDigits())
        core.setParseBigDecimal(true); core.setParseStrict(true); core.setParseIntegerOnly(true)
        core.setDecimalPatternMatchRequired(true); core.setParseNoExponent(true); core.setParseCaseSensitive(true)
        assertTrue(core.isParseBigDecimal()); assertTrue(core.isParseStrict()); assertTrue(core.isParseIntegerOnly())
        assertTrue(core.isDecimalPatternMatchRequired()); assertTrue(core.isParseNoExponent()); assertTrue(core.isParseCaseSensitive())
        core.setParseStrictMode(null)
        assertFalse(core.isParseStrict())
    }
}
