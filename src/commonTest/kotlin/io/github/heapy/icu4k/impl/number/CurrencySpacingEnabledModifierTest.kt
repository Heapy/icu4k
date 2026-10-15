package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.text.NumberFormatField
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class CurrencySpacingEnabledModifierTest {
    private fun currency(text: String) = FormattedStringBuilder().apply { append(text, NumberFormatField.CURRENCY) }
    @Test fun preparedSpacingRetainsSourceIndexBehavior() {
        val symbols = DecimalFormatSymbols(ULocale("en"))
        val modifier = CurrencySpacingEnabledModifier(currency("USD"), currency("EUR"), false, true, symbols)
        val output = FormattedStringBuilder().apply { append("1", NumberFormatField.INTEGER) }
        assertEquals(7, modifier.apply(output, 0, 1))
        assertEquals("USD 1EUR", output.toString()) // Source checks the old right index after prefix insertion.
        assertNull(output.fieldAt(3))
        assertSame(NumberFormatField.INTEGER, output.fieldAt(4))
        assertEquals(3, modifier.getPrefixLength())
        assertEquals(6, modifier.getCodePointCount())
        assertTrue(modifier.isStrong())
    }
    @Test fun inPlaceSpacingHandlesSupplementaryBoundariesAndFields() {
        val symbols = DecimalFormatSymbols(ULocale("en"))
        for (before in listOf(false, true)) {
            symbols.setPatternForCurrencySpacing(0, before, "[😀]")
            symbols.setPatternForCurrencySpacing(1, before, "[𝟘]")
            symbols.setPatternForCurrencySpacing(2, before, "::")
        }
        val output = currency("😀").apply {
            append("𝟘", NumberFormatField.INTEGER)
            append(currency("😀"))
        }
        assertEquals(4, CurrencySpacingEnabledModifier.applyCurrencySpacing(output, 0, 2, 4, 2, symbols))
        assertEquals("😀::𝟘::😀", output.toString())
        assertEquals(listOf(1, 1, 0, 0, 2, 2, 0, 0, 1, 1), output.toFieldArray().map {
            when (it) { NumberFormatField.CURRENCY -> 1; NumberFormatField.INTEGER -> 2; else -> 0 }
        })
    }
    @Test fun emptyNumberDoesNotReadSymbols() {
        assertEquals(0, CurrencySpacingEnabledModifier.applyCurrencySpacing(null, 0, 3, 3, 3, null))
        val modifier = CurrencySpacingEnabledModifier(currency(""), currency(""), false, false, null)
        val output = FormattedStringBuilder()
        assertEquals(0, modifier.apply(output, 0, 0))
    }
}
