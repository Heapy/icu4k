@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.number.MacroProps
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class NumberFormatterFluentTest {
    @Test fun fluentSkeletonMeasuresAndLocaleRemoval() {
        val base = NumberFormatter.with().precision(Precision.fixedFraction(2))
        val formatter = base.unit(Currency.getInstance("USD")).locale(ULocale.ENGLISH)
        assertEquals("$12.34", formatter.format(BigDecimal("12.345")).toString())
        assertEquals("1.25 m", formatter.format(Measure(BigDecimal("1.25"), MeasureUnit.METER)).toString())
        assertEquals("$1.25", formatter.format(BigDecimal("1.25")).toString())
        assertEquals("currency/USD .00", formatter.toSkeleton())
        assertEquals(formatter, formatter.withoutLocale().locale(ULocale.ENGLISH))
        val mixed = NumberFormatter.forSkeleton("unit/foot-and-inch unit-width-full-name").locale(ULocale.ENGLISH)
        assertEquals("1 foot, 6 inches", mixed.format(1.5).toString())
        assertSame(NumberFormatter.forSkeleton("group-off"), NumberFormatter.forSkeleton("group-off"))
        assertEquals(NumberFormatter.with().threshold(0), NumberFormatter.with().threshold(5))
        assertEquals("12", NumberFormatter.withLocale(ULocale.ENGLISH).format(12L).toString())
        assertEquals("12.5", NumberFormatter.withLocale(ULocale.ENGLISH).format(12.5).toString())
    }
    @Test fun settingsCloneSymbolsAndResolveOnce() {
        val symbols = DecimalFormatSymbols(ULocale.ENGLISH)
        val formatter = NumberFormatter.with().symbols(symbols).locale(ULocale.ENGLISH)
        symbols.setDecimalSeparatorString("!")
        assertEquals("1.5", formatter.format(1.5).toString())
        val macros = MacroProps().also { it.unit = MeasureUnit.METER; it.threshold = 1L }
        val configured = NumberFormatter.with().macros(macros)
        assertEquals("unit/meter", configured.toSkeleton())
        macros.unit = MeasureUnit.FOOT
        assertEquals("unit/meter", configured.toSkeleton())
        assertEquals(3L, configured.resolve().threshold)
        val case = NumberFormatter.with().unitDisplayCase("dative").displayOptions(DisplayOptions.builder().build())
        assertEquals("", case.toSkeleton())
        assertFailsWith<NullPointerException> { NumberFormatter.with().displayOptions(DisplayOptions.builder().setGrammaticalCase(null).build()) }
    }
    @Test fun portableFormatAdapterFieldsAndParsingPolicy() {
        val formatter = NumberFormatter.withLocale(ULocale.ENGLISH).precision(Precision.fixedFraction(2))
        val adapter = formatter.toFormat()
        assertSame(formatter, adapter.getNumberFormatter())
        assertEquals("12.50", adapter.format(12.5))
        val buffer = StringBuilder("prefix:")
        val position = FormatFieldPosition(NumberFormatField.FRACTION)
        assertSame(buffer, adapter.format(12.5, buffer, position))
        assertEquals("prefix:12.50", buffer.toString())
        assertEquals(10, position.getBeginIndex()); assertEquals(12, position.getEndIndex())
        assertEquals('1', adapter.formatToCharacterIterator(12.5).first())
        assertFailsWith<UnsupportedOperationException> { adapter.parseObject("12.5", ParsePosition(0)) }
        assertFailsWith<IllegalArgumentException> { adapter.format("12.5") }
    }
    @Test fun genericNumbersKeepFractionsSignedZeroAndSpecialValues() {
        val formatter = NumberFormatter.withLocale(ULocale.ENGLISH)
        val cases = listOf<Number>(12.5, -0.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)
        val expected = listOf("12.5", "-0", "NaN", "∞", "-∞")
        for (i in cases.indices) {
            assertEquals(expected[i], formatter.format(cases[i]).toString())
            assertEquals(expected[i], formatter.toFormat().format(cases[i]))
        }
    }

}
