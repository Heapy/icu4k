// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class PluralFormatTest {
    @Test fun constructorsFormattingAndPatternFailure() {
        val format = PluralFormat(ULocale.ENGLISH, "offset:1 =0{nobody} one{you and one} other{you and #}")
        assertEquals("nobody", format.format(0.0))
        assertEquals("you and one", format.format(2.0))
        assertEquals("you and 3", format.format(4.0))
        val output = StringBuilder("prefix ")
        val field = FormatFieldPosition(0).also { it.setBeginIndex(8); it.setEndIndex(9) }
        assertSame(output, format.format(4L, output, field))
        assertEquals("prefix you and 3", output.toString())
        assertEquals(8, field.getBeginIndex())
        assertEquals(9, field.getEndIndex())
        assertFailsWith<IllegalArgumentException> { format.format("bad", null as StringBuilder?, null) }
        assertFailsWith<UnsupportedOperationException> { format.parse(null, null) }
        assertFailsWith<UnsupportedOperationException> { format.parseObject(null, null as ParsePosition?) }
        assertFailsWith<NullPointerException> { format.hashCode() }
        assertFailsWith<IllegalArgumentException> { format.applyPattern("one{unterminated") }
        assertNull(format.toPattern())
        assertEquals("1,234.5", format.format(1234.5))
        val ordinal = PluralFormat(ULocale.ENGLISH, PluralRules.PluralType.ORDINAL, "one{#st} two{#nd} few{#rd} other{#th}")
        assertEquals("22nd", ordinal.format(22.0))
    }
    @Test fun cloneSharesMessageAndOriginalSelector() {
        val pattern = "one{one} few{few} other{other}"
        val original = PluralFormat(ULocale.ENGLISH, pattern)
        val copy = original.clone()
        assertEquals(original, copy)
        original.setLocale(ULocale("ru"))
        assertEquals(pattern, copy.toPattern())
        // Source resets the shared MessagePattern, but leaves the clone's pattern string intact.
        assertEquals("2", copy.format(2.0))
        original.applyPattern(pattern)
        // The clone's selector remains bound to the original formatter's new Russian rules.
        assertEquals("few", copy.format(2.0))
        assertNotEquals(original, copy)
    }
    @Test fun customRulesNumberFormatAndLocaleReset() {
        val rules = PluralRules.createRules("one: n = 7")!!
        val format = PluralFormat(ULocale.ENGLISH, rules, "one{seven} other{# items}")
        assertEquals("seven", format.format(7.0))
        val numbers = DecimalFormat("0.00", DecimalFormatSymbols(ULocale.ENGLISH))
        format.setNumberFormat(numbers)
        assertEquals("2.00 items", format.format(2.0))
        numbers.setMinimumFractionDigits(3)
        assertEquals("2.000 items", format.format(2.0))
        format.setNumberFormat(null)
        assertFailsWith<NullPointerException> { format.format(2.0) }
        format.setLocale(ULocale.ENGLISH)
        assertNull(format.toPattern())
        assertEquals("2", format.format(2.0))
        assertNull(format.getLocale(ULocale.VALID_LOCALE))
    }
}
