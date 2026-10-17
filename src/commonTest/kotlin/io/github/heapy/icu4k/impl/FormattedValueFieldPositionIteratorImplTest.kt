@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.*
import kotlin.test.*

class FormattedValueFieldPositionIteratorImplTest {
    private class Span : FormatSpanField("interval-test")
    private val span = Span()
    private fun item(field: FormatField?, start: Int, limit: Int) = FormatFieldPosition(field).also {
        it.setBeginIndex(start); it.setEndIndex(limit)
    }
    private fun fields() = mutableListOf(
        item(DateFormat.Field.MONTH, 0, 3), item(DateFormat.Field.DAY_OF_MONTH, 4, 5),
        item(DateFormat.Field.MONTH, 8, 11), item(DateFormat.Field.DAY_OF_MONTH, 12, 14),
        item(DateFormat.Field.YEAR, 16, 20))
    private fun rows(items: List<FormatFieldPosition>, position: ConstrainedFieldPosition = ConstrainedFieldPosition()): List<List<Any?>> = buildList {
        while (FormattedValueFieldPositionIteratorImpl.nextPosition(items, position)) add(listOf(
            position.getField(), position.getFieldValue(), position.getStart(), position.getLimit()))
    }
    @Test fun orderedFieldsEndpointValuesAndIndependentConstraints() {
        val items = fields()
        FormattedValueFieldPositionIteratorImpl.addOverlapSpans(items, span, 1)
        FormattedValueFieldPositionIteratorImpl.sort(items)
        assertEquals(listOf(
            listOf(span, 1, 0, 5), listOf(DateFormat.Field.MONTH, null, 0, 3),
            listOf(DateFormat.Field.DAY_OF_MONTH, null, 4, 5), listOf(span, 0, 8, 14),
            listOf(DateFormat.Field.MONTH, null, 8, 11), listOf(DateFormat.Field.DAY_OF_MONTH, null, 12, 14),
            listOf(DateFormat.Field.YEAR, null, 16, 20)), rows(items))
        val position = ConstrainedFieldPosition()
        position.constrainFieldAndValue(span, 0)
        assertEquals(listOf(listOf(span, 0, 8, 14)), rows(items, position))
        assertEquals(7L, position.getInt64IterationContext())
        assertFalse(FormattedValueFieldPositionIteratorImpl.nextPosition(items, position))
        assertEquals(8, position.getStart()); assertEquals(14, position.getLimit())
        position.reset(); position.constrainClass(Span::class)
        assertEquals(listOf(listOf(span, 1, 0, 5), listOf(span, 0, 8, 14)), rows(items, position))
        position.reset(); position.constrainField(DateFormat.Field.MONTH)
        assertEquals(2, rows(items, position).size)
    }
    @Test fun iteratorCarriesBothNormalAndSpanAttributes() {
        val items = fields()
        FormattedValueFieldPositionIteratorImpl.addOverlapSpans(items, span, 0)
        val iterator = FormattedValueFieldPositionIteratorImpl.toCharacterIterator("Jan 1 – Feb 12, 2024!", items)
        iterator.setIndex(1)
        assertEquals(0, iterator.getAttribute(span))
        assertSame(DateFormat.Field.MONTH, iterator.getAttribute(DateFormat.Field.MONTH))
        assertEquals(0, iterator.getRunStart(span)); assertEquals(5, iterator.getRunLimit(span))
        iterator.setIndex(6); assertTrue(iterator.getAttributes().isEmpty())
        iterator.setIndex(13); assertEquals(1, iterator.getAttribute(span))
        iterator.setIndex(17); assertEquals(setOf<FormatAttribute>(DateFormat.Field.YEAR), iterator.getAttributes().keys)
    }
    @Test fun duplicateScanUsesFirstLaterMatchAndDoesNotRequireDisjointRanges() {
        val items = mutableListOf(item(DateFormat.Field.YEAR, 2, 4), item(DateFormat.Field.YEAR, 5, 7), item(DateFormat.Field.YEAR, 8, 10))
        FormattedValueFieldPositionIteratorImpl.addOverlapSpans(items, span, 4)
        val position = ConstrainedFieldPosition().also { it.constrainField(span) }
        assertEquals(listOf(listOf(span, 4, 2, 7), listOf(span, -3, 5, 10)), rows(items, position))
        val unique = mutableListOf(item(DateFormat.Field.MONTH, 0, 1), item(DateFormat.Field.YEAR, 2, 3))
        FormattedValueFieldPositionIteratorImpl.addOverlapSpans(unique, span, 0)
        assertEquals(2, unique.size)
    }
    @Test fun iterationContextAndFailureMutationFollowSource() {
        val position = ConstrainedFieldPosition()
        position.setInt64IterationContext(-1)
        assertFailsWith<IndexOutOfBoundsException> { FormattedValueFieldPositionIteratorImpl.nextPosition(fields(), position) }
        assertEquals(-1L, position.getInt64IterationContext())
        position.setInt64IterationContext(100)
        assertFalse(FormattedValueFieldPositionIteratorImpl.nextPosition(fields(), position))
        assertEquals(101L, position.getInt64IterationContext())
        position.reset()
        assertFailsWith<IllegalArgumentException> { FormattedValueFieldPositionIteratorImpl.nextPosition(listOf(item(null, 0, 1)), position) }
        assertEquals(0L, position.getInt64IterationContext())
    }
}
