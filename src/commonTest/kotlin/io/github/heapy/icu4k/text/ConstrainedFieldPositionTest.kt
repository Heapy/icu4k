@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import kotlin.test.*

class ConstrainedFieldPositionTest {
    @Test fun constraintChangesRetainRangeAndContext() {
        val value = ConstrainedFieldPosition()
        assertEquals("CFPos[0-0 null]", value.toString())
        value.setState(NumberFormatField.INTEGER, "saved", -4, 12)
        value.setInt64IterationContext(Long.MIN_VALUE)
        value.constrainClass(NumberFormatField::class)
        assertNull(value.getField())
        assertNull(value.getFieldValue())
        assertEquals(-4, value.getStart())
        assertEquals(12, value.getLimit())
        assertEquals(Long.MIN_VALUE, value.getInt64IterationContext())
        assertTrue(value.matchesField(NumberFormatField.CURRENCY, null))
        value.constrainField(NumberFormatField.INTEGER)
        assertTrue(value.matchesField(NumberFormatField.INTEGER, "other"))
        assertFalse(value.matchesField(NumberFormatField.FRACTION, null))
        value.setState(NumberFormatField.FRACTION, null, 5, 1)
        assertTrue(value.matchesField(NumberFormatField.FRACTION, null))
        value.reset()
        assertTrue(value.matchesField(NumberFormatField.CURRENCY, null))
        assertEquals(0L, value.getInt64IterationContext())
        assertEquals(0, value.getStart())
    }
    @Test fun identityValuesNullRulesAndEqualityDirection() {
        val value = ConstrainedFieldPosition()
        val mutable = FormattedStringBuilder()
        value.constrainFieldAndValue(NumberFormatField.INTEGER, mutable)
        assertTrue(value.matchesField(NumberFormatField.INTEGER, mutable))
        assertFailsWith<UnsupportedOperationException> { value.matchesField(NumberFormatField.INTEGER, FormattedStringBuilder()) }
        value.constrainFieldAndValue(null, "test")
        assertFalse(value.matchesField(NumberFormatField.INTEGER, "test"))
        assertFailsWith<IllegalArgumentException> { value.matchesField(null, null) }
        assertFailsWith<IllegalArgumentException> { value.constrainField(null) }
        assertFailsWith<IllegalArgumentException> { value.constrainClass(null) }
    }
    private object ResetCount { var value = 0 }
    @Test fun constructorInvokesVirtualReset() {
        ResetCount.value = 0
        val value = object : ConstrainedFieldPosition() { override fun reset() { ResetCount.value++ } }
        assertEquals(1, ResetCount.value)
        assertFailsWith<NullPointerException> { value.matchesField(NumberFormatField.INTEGER, null) }
        value.constrainField(NumberFormatField.INTEGER)
        assertTrue(value.matchesField(NumberFormatField.INTEGER, null))
    }
    @Test fun fractionDigitMetadataDoesNotChangeBasePosition() {
        val field = UFieldPosition(NumberFormatField.FRACTION, 7)
        field.setBeginIndex(-2); field.setEndIndex(99)
        assertEquals(-1, field.getCountVisibleFractionDigits())
        field.setFractionDigits(-3, Long.MAX_VALUE)
        assertEquals(-3, field.getCountVisibleFractionDigits())
        assertEquals(Long.MAX_VALUE, field.getFractionDigits())
        assertEquals(7, field.getField())
        assertSame(NumberFormatField.FRACTION, field.getFieldAttribute())
        assertEquals(-2, field.getBeginIndex())
        assertEquals(99, field.getEndIndex())
        assertEquals(field, UFieldPosition(NumberFormatField.FRACTION, 7).apply { setBeginIndex(-2); setEndIndex(99) })
    }
}
