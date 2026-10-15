package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.number.*
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.text.NumberFormatField as Field
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class NumberFormatterOutputTest {
    @Test fun localizedDigitsAndFractionFields() {
        val micros = MicroProps(true).also {
            it.symbols = DecimalFormatSymbols(ULocale("ar@numbers=arab"))
            it.grouping = Grouper.forStrategy(GroupingStrategy.OFF)
            it.decimal = DecimalSeparatorDisplay.AUTO
        }
        val quantity = DecimalQuantity_DualStorageBCD(12.5)
        quantity.roundToInfinity()
        val buffer = FormattedStringBuilder()
        assertEquals(4, NumberFormatterOutput.writeNumber(micros, quantity, buffer, 0))
        assertEquals("١٢٫٥", buffer.toString())
        assertSame(Field.INTEGER, buffer.fieldAt(0))
        assertSame(Field.DECIMAL_SEPARATOR, buffer.fieldAt(2))
        assertSame(Field.FRACTION, buffer.fieldAt(3))
    }
    @Test fun paddingReturnValueAndZeroFallback() {
        val micros = MicroProps(true).also {
            it.symbols = DecimalFormatSymbols(ULocale.ENGLISH)
            it.grouping = Grouper.forStrategy(GroupingStrategy.OFF)
            it.decimal = DecimalSeparatorDisplay.AUTO
            it.modInner = ConstantAffixModifier("(", ")", Field.SIGN, true)
            it.modMiddle = ConstantAffixModifier("$", "", Field.CURRENCY, false)
            it.modOuter = ConstantAffixModifier("", " m", Field.MEASURE_UNIT, false)
            it.padding = Padder("_", 10, Padder.PadPosition.BEFORE_PREFIX)
        }
        val quantity = DecimalQuantity_DualStorageBCD(0)
        quantity.setMinInteger(0)
        val buffer = FormattedStringBuilder()
        assertEquals(1, NumberFormatterOutput.writeNumber(micros, quantity, buffer, 0))
        assertEquals("0", buffer.toString())
        assertEquals(2, NumberFormatterOutput.writeAffixes(micros, buffer, 0, 1))
        assertEquals("____$(0) m", buffer.toString())
    }
    @Test fun multiCharacterDigitsAndSupplementaryDigits() {
        val symbols = DecimalFormatSymbols(ULocale.ENGLISH)
        val micros = MicroProps(true).also {
            it.symbols = symbols
            it.grouping = Grouper.forStrategy(GroupingStrategy.OFF)
            it.decimal = DecimalSeparatorDisplay.AUTO
        }
        for (digits in listOf(Array(10) { "<$it>" }, Array(10) { io.github.heapy.icu4k.text.UTF16.valueOf(0x1d7ce + it) })) {
            symbols.setDigitStrings(digits)
            val quantity = DecimalQuantity_DualStorageBCD(12.5)
            quantity.roundToInfinity()
            val buffer = FormattedStringBuilder()
            val expected = digits[1] + digits[2] + "." + digits[5]
            assertEquals(expected.length, NumberFormatterOutput.writeNumber(micros, quantity, buffer, 0))
            assertEquals(expected, buffer.toString())
            for (index in digits[1].indices) assertSame(Field.INTEGER, buffer.fieldAt(index))
        }
    }

}
