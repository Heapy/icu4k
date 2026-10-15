@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.number.NumberRangeFormatter.*
import io.github.heapy.icu4k.text.ConstrainedFieldPosition
import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.MeasureUnit
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class NumberRangeFormatterTest {
    @Test fun defaultsIdentityAndCollapsedUnits() {
        val range = NumberRangeFormatter.withLocale(ULocale.ENGLISH)
        assertEquals("3–5", range.formatRange(3, 5).toString())
        assertEquals("~3", range.formatRange(3, 3).toString())
        assertEquals(RangeIdentityResult.EQUAL_BEFORE_ROUNDING, range.formatRange(3, 3).getIdentityResult())
        val rounded = range.numberFormatterBoth(NumberFormatter.with().precision(Precision.integer()))
        assertEquals("~1", rounded.formatRange(1.1, 1.2).toString())
        assertEquals(RangeIdentityResult.EQUAL_AFTER_ROUNDING, rounded.formatRange(1.1, 1.2).getIdentityResult())
        assertEquals("1", rounded.identityFallback(RangeIdentityFallback.SINGLE_VALUE).formatRange(1.1, 1.2).toString())
        val units = range.numberFormatterBoth(NumberFormatter.with().unit(MeasureUnit.METER).unitWidth(UnitWidth.FULL_NAME))
        val result = units.formatRange(1, 2)
        assertEquals("1–2 meters", result.toString())
        val position = ConstrainedFieldPosition().also { it.constrainField(SpanField.NUMBER_RANGE_SPAN) }
        assertTrue(result.nextPosition(position)); assertEquals(0, position.getFieldValue()); assertEquals(0, position.getStart()); assertEquals(1, position.getLimit())
        assertTrue(result.nextPosition(position)); assertEquals(1, position.getFieldValue()); assertEquals(2, position.getStart()); assertEquals(3, position.getLimit())
        assertFalse(result.nextPosition(position))
        assertEquals("1", result.getFirstBigDecimal().toString()); assertEquals("2", result.getSecondBigDecimal().toString())
    }
    @Test fun genericNumbersLocaleRemovalAndNulls() {
        val formatter = NumberRangeFormatter.withLocale(ULocale.ENGLISH)
        val a: Number = BigDecimal("1.0"); val b: Number = BigDecimal("1.00")
        assertEquals(RangeIdentityResult.EQUAL_AFTER_ROUNDING, formatter.formatRange(a, b).getIdentityResult())
        val input: Number = 12.5
        assertEquals("12.5–13.5", formatter.formatRange(input, 13.5 as Number).toString())
        assertEquals(formatter, formatter.withoutLocale().locale(ULocale.ENGLISH))
        assertFailsWith<IllegalArgumentException> { formatter.formatRange(null as Number?, b) }
        assertEquals(RangeIdentityResult.EQUAL_AFTER_ROUNDING, formatter.formatRange(Double.NaN, Double.NaN).getIdentityResult())
        val sameNaN: Number = Double.NaN
        assertEquals(RangeIdentityResult.EQUAL_BEFORE_ROUNDING, formatter.formatRange(sameNaN, sameNaN).getIdentityResult())
    }
}
