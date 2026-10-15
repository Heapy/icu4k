@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.number.*
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class FormattedNumberTest {
    @Test fun textFieldsExactDecimalAndGender() {
        val quantity = DecimalQuantity_DualStorageBCD(BigDecimal("12.345"))
        val buffer = FormattedStringBuilder()
        val micros = NumberFormatterImpl(MacroProps().also {
            it.loc = ULocale("de"); it.unit = MeasureUnit.METER; it.unitWidth = UnitWidth.FULL_NAME
            it.precision = Precision.fixedFraction(2)
        }).format(quantity, buffer)
        val result = FormattedNumber(buffer, quantity, micros.outputUnit, micros.gender)
        assertEquals("12,34 Meter", result.toString())
        assertEquals("12.34", result.toBigDecimal().toString())
        assertEquals(DisplayOptions.NounClass.MASCULINE, result.getNounClass())
        assertSame(quantity, result.getFixedDecimal())
        assertIs<String>(result.subSequence(0, 2))
        val append = StringBuilder("="); assertSame(append, result.appendTo(append)); assertEquals("=12,34 Meter", append.toString())
        val position = ConstrainedFieldPosition().also { it.constrainField(NumberFormatField.FRACTION) }
        assertTrue(result.nextPosition(position)); assertEquals(3, position.getStart()); assertEquals(5, position.getLimit())
        val iterator = result.toCharacterIterator(); assertEquals('1', iterator.first()); assertEquals('r', iterator.last())
    }
    @Test fun nullableGenderAndOversizedDecimal() {
        val quantity = DecimalQuantity_DualStorageBCD(BigDecimal("18446744073709551617"))
        val buffer = FormattedStringBuilder()
        val micros = NumberFormatterImpl(MacroProps().also { it.loc = ULocale.ENGLISH }).format(quantity, buffer)
        val result = FormattedNumber(buffer, quantity, micros.outputUnit, null)
        assertEquals("18,446,744,073,709,551,617", result.toString())
        assertEquals("18446744073709551617", result.toBigDecimal().toString())
        assertEquals("", result.getGender()); assertEquals(DisplayOptions.NounClass.UNDEFINED, result.getNounClass())
    }
}
