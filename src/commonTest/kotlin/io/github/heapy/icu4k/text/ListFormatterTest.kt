@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class ListFormatterTest {
    @Test fun localeListsAndContexts() {
        assertEquals("a, b, and c", ListFormatter.getInstance(ULocale("en")).format("a", "b", "c"))
        assertEquals("a e isla", ListFormatter.getInstance(ULocale("es")).format("a", "isla"))
        assertEquals("a y hielo", ListFormatter.getInstance(ULocale("es")).format("a", "hielo"))
        assertEquals("a u ocho", ListFormatter.getInstance(ULocale("es"), ListFormatter.Type.OR, ListFormatter.Width.WIDE).format("a", "ocho"))
        assertEquals("א ו-abc", ListFormatter.getInstance(ULocale("he")).format("א", "abc"))
        assertEquals("א וב", ListFormatter.getInstance(ULocale("he")).format("א", "ב"))
    }
    @Test fun reversedArgumentsSpansAndAppend() {
        val formatter = ListFormatter("[{1}/{0}]", "{1}|{0}", "{1}|{0}", "{1}|{0}")
        val value = formatter.formatToValue("😀", "b")
        assertEquals("[b/😀]", value.toString())
        val position = ConstrainedFieldPosition()
        position.constrainField(ListFormatter.SpanField.LIST_SPAN)
        assertTrue(value.nextPosition(position)); assertEquals(1, position.getFieldValue()); assertEquals(1, position.getStart()); assertEquals(2, position.getLimit())
        assertTrue(value.nextPosition(position)); assertEquals(0, position.getFieldValue()); assertEquals(3, position.getStart()); assertEquals(5, position.getLimit())
        assertFalse(value.nextPosition(position))
        val buffer = StringBuilder("prefix")
        assertSame(buffer, value.appendTo(buffer)); assertEquals("prefix[b/😀]", buffer.toString())
        assertEquals('b', value[1]); assertEquals("😀", value.subSequence(3, 5))
        assertEquals("[{1}/{0}]", formatter.getPatternForNumItems(2))
        assertFailsWith<NullPointerException> { formatter.format("a", null) }
        assertFailsWith<IllegalArgumentException> { formatter.getPatternForNumItems(0) }
    }
}
