package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.*
import kotlin.test.*

class FormattedValueStringBuilderTest {
    @Test fun numericCoalescingAndFieldTrimming() {
        val value = FormattedStringBuilder().apply {
            append("1", NumberFormatField.INTEGER); append(",", NumberFormatField.GROUPING_SEPARATOR)
            append("234", NumberFormatField.INTEGER); append(".", NumberFormatField.DECIMAL_SEPARATOR)
            append(" \u200e56\ufe0f ", NumberFormatField.FRACTION)
        }
        val position = ConstrainedFieldPosition()
        val spans = mutableListOf<Triple<FormatField?, Int, Int>>()
        while (FormattedValueStringBuilderImpl.nextPosition(value, position, null)) spans.add(Triple(position.getField(), position.getStart(), position.getLimit()))
        assertEquals<List<Triple<FormatField?, Int, Int>>>(listOf(Triple(NumberFormatField.GROUPING_SEPARATOR, 1, 2), Triple(NumberFormatField.INTEGER, 0, 5),
            Triple(NumberFormatField.DECIMAL_SEPARATOR, 5, 6), Triple(NumberFormatField.FRACTION, 8, 10)), spans)
        val fraction = FormatFieldPosition(NumberFormatField.FRACTION)
        assertTrue(FormattedValueStringBuilderImpl.nextFieldPosition(value, fraction))
        assertEquals(8, fraction.getBeginIndex())
        assertEquals(10, fraction.getEndIndex())
    }
    @Test fun listSpansEmitElementAfterSpanWithoutTrimming() {
        val value = FormattedStringBuilder().apply { append(" a ", ListFormatField.ELEMENT) }
        FormattedValueStringBuilderImpl.applySpanRange(value, ListSpanField.LIST_SPAN, 7, 0, 3)
        assertEquals(0, FormattedValueStringBuilderImpl.findSpan(value, 7))
        assertEquals(-1, FormattedValueStringBuilderImpl.findSpan(value, 8))
        val position = ConstrainedFieldPosition()
        assertTrue(FormattedValueStringBuilderImpl.nextPosition(value, position, null))
        assertSame(ListSpanField.LIST_SPAN, position.getField())
        assertEquals(7, position.getFieldValue())
        assertTrue(FormattedValueStringBuilderImpl.nextPosition(value, position, null))
        assertSame(ListFormatField.ELEMENT, position.getField())
        assertEquals(0, position.getStart())
        assertEquals(3, position.getLimit())
        assertFalse(FormattedValueStringBuilderImpl.nextPosition(value, position, null))
    }
}
