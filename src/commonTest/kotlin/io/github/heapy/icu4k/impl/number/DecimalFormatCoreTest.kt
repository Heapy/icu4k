// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*
import io.github.heapy.icu4k.math.BigDecimal as IcuBigDecimal
import kotlin.test.*

class DecimalFormatCoreTest {
    private fun format(core: DecimalFormatCore, value: Number): String = core.format(value, StringBuilder(), FormatFieldPosition(0)).toString()
    @Test fun patternUpdatesPublishFormatterAndInvalidateParsers() {
        val core = DecimalFormatCore.create("#,##0.00", DecimalFormatSymbols(ULocale("en")))
        val parser = core.getParser(); val currencyParser = core.getCurrencyParser()
        assertSame(parser, core.getParser()); assertSame(currencyParser, core.getCurrencyParser())
        val oldFormatter = core.toNumberFormatter()
        assertEquals("1,234.50", format(core, BigDecimal("1234.5")))
        core.setProperties { it.setPositivePrefix("literal+"); it.setMinimumFractionDigits(3); it.setMaximumFractionDigits(3) }
        assertNotSame(parser, core.getParser()); assertNotSame(currencyParser, core.getCurrencyParser())
        assertEquals("literal+1,234.500", format(core, BigDecimal("1234.5")))
        assertEquals("1,234.50", oldFormatter.format(BigDecimal("1234.5")).toString())
        core.applyPattern("0.0")
        assertEquals("1234.5", format(core, BigDecimal("1234.5")))
        val copy = core.copy()
        assertEquals(core, copy); assertNotSame(core.properties, copy.properties); assertNotSame(core.symbols, copy.symbols)
        copy.applyPattern("0.000")
        assertEquals("1234.500", format(copy, BigDecimal("1234.5")))
        assertEquals("1234.5", format(core, BigDecimal("1234.5")))
    }
    @Test fun positionsParsingAndLegacyDecimalResults() {
        val core = DecimalFormatCore.create("#,##0.00", DecimalFormatSymbols(ULocale("en")))
        val pos = UFieldPosition(1)
        val result = StringBuilder("x:")
        assertSame(result, core.format(1234.5, result, pos))
        assertEquals("x:1,234.50", result.toString())
        assertEquals(8, pos.getBeginIndex()); assertEquals(10, pos.getEndIndex())
        assertEquals(2, pos.getCountVisibleFractionDigits()); assertEquals(50L, pos.getFractionDigits())
        val parsePosition = ParsePosition(2).also { it.errorIndex = 99 }
        val parsed = core.parse("xx1234.50tail", parsePosition)
        assertTrue(parsed is IcuBigDecimal); assertEquals("1234.5", parsed.toString())
        assertEquals(9, parsePosition.index); assertEquals(99, parsePosition.errorIndex)
        assertEquals(12L, core.parse("12", null))
        assertNull(core.parse("", ParsePosition(0)))
        assertEquals("Text cannot be null", assertFailsWith<IllegalArgumentException> { core.parse(null, null) }.message)
        assertFailsWith<IllegalArgumentException> { core.parse("1", ParsePosition(-1)) }
        val currency = core.parseCurrency("USD 12.50", null)!!
        assertEquals("USD", currency.getCurrency().getCurrencyCode())
        assertEquals("12.5", currency.getNumber().toString())
    }
    @Test fun anyNumberBoundaryAndExtremeDecimalFallback() {
        val core = DecimalFormatCore.create("0.00", DecimalFormatSymbols(ULocale("en")))
        val values: List<Number> = listOf(BigDecimal("12.5"), BigInteger("12345678901234567890"), IcuBigDecimal("12.5"), 12L, 12.5)
        for (value in values) {
            val iterator = core.formatToCharacterIterator(value)
            val text = buildString { var c = iterator.first(); while (c != FORMAT_CHARACTER_DONE) { append(c); c = iterator.next() } }
            assertEquals(format(core, value), text)
            assertEquals(text, core.toNumberFormatter().toFormat().format(value))
        }
        assertFailsWith<IllegalArgumentException> { core.formatToCharacterIterator("12") }
        assertFailsWith<IllegalArgumentException> { core.formatToCharacterIterator(null) }
        class UnsupportedNumber : Number() {
            override fun toDouble() = 1.0
            override fun toFloat() = 1f
            override fun toLong() = 1L
            override fun toInt() = 1
            override fun toShort() = 1.toShort()
            override fun toByte() = 1.toByte()
        }
        val error = assertFailsWith<IllegalArgumentException> { core.formatToCharacterIterator(UnsupportedNumber()) }
        assertTrue(error.message!!.startsWith("Number is of an unsupported type:"))
        assertEquals(Double.POSITIVE_INFINITY, DecimalFormatCore.safeConvertBigDecimal(BigDecimal("1E+1000000000")))
        assertEquals(Double.NEGATIVE_INFINITY, DecimalFormatCore.safeConvertBigDecimal(BigDecimal("-1E+1000000000")))
        assertEquals((-0.0).toBits(), (DecimalFormatCore.safeConvertBigDecimal(BigDecimal("-1E-1000000000")) as Double).toBits())
        assertEquals(0.0, DecimalFormatCore.safeConvertBigDecimal(BigDecimal("1E-1000000000")))
        assertEquals(Double.NEGATIVE_INFINITY, DecimalFormatCore.safeConvertBigDecimal(BigDecimal("0E+1000000000")))
    }
}
