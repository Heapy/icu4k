package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.FormattedValueStringBuilderImpl
import kotlin.test.*

class FormatAttributedCharacterIteratorTest {
    @Test fun exportedNumberHasOverlappingIntegerAndGroupingFields() {
        val text = FormattedStringBuilder()
        text.append("1", NumberFormatField.INTEGER)
        text.append(",", NumberFormatField.GROUPING_SEPARATOR)
        text.append("234", NumberFormatField.INTEGER)
        val iterator = FormattedValueStringBuilderImpl.toCharacterIterator(text, null)
        assertEquals('1', iterator.first())
        assertEquals(0, iterator.getRunStart(NumberFormatField.INTEGER))
        assertEquals(5, iterator.getRunLimit(NumberFormatField.INTEGER))
        assertEquals(',', iterator.next())
        assertEquals(setOf<FormatAttribute>(NumberFormatField.INTEGER, NumberFormatField.GROUPING_SEPARATOR), iterator.getAttributes().keys)
        val clone = iterator.clone() as FormatAttributedCharacterIterator
        assertEquals('4', clone.last())
        assertEquals(1, iterator.getIndex())
        assertEquals(FORMAT_CHARACTER_DONE, clone.next())
        assertEquals(5, clone.getRunStart())
        assertEquals(emptyMap(), clone.getAttributes())
    }
    @Test fun adjacentEqualValuesRetainUnconstrainedRunBoundaries() {
        val iterator = attributedCharacterIterator("abcd", listOf(
            FormatAttributeRange(NumberFormatField.INTEGER, 1, 0, 2),
            FormatAttributeRange(NumberFormatField.INTEGER, 1, 2, 4)))
        iterator.setIndex(2)
        assertEquals(2, iterator.getRunStart())
        assertEquals(0, iterator.getRunStart(NumberFormatField.INTEGER))
        assertEquals(4, iterator.getRunLimit(NumberFormatField.INTEGER))
    }
}
