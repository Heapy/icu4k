package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ParseException
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class UFormatTest {
    private class Sample : UFormat() {
        override fun format(input: Any?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder {
            position!!.setBeginIndex(output!!.length); output.append(input); position.setEndIndex(output.length); return output
        }
        override fun parseObject(source: String?, position: ParsePosition?): Any? {
            if (source!!.startsWith("ok", position!!.index)) { position.index += 2; return "accepted" }
            position.errorIndex = position.index + 1; return null
        }
        fun copy(): Sample = (cloneHost() as Sample?) ?: Sample().also { copyLocaleTo(it) }
    }
    @Test fun localePairsAreAtomicAndReferencesArePreserved() {
        val format = Sample()
        assertNull(format.getLocale(null)); assertNull(format.getLocale(ULocale.ACTUAL_LOCALE))
        val valid = ULocale("en_US"); val actual = ULocale("en")
        format.setLocale(valid, actual)
        assertSame(valid, format.getLocale(null)); assertSame(actual, format.getLocale(ULocale.ACTUAL_LOCALE))
        assertFailsWith<IllegalArgumentException> { format.setLocale(null, actual) }
        assertFailsWith<IllegalArgumentException> { format.setLocale(valid, null) }
        assertSame(valid, format.getLocale(ULocale.VALID_LOCALE)); assertSame(actual, format.getLocale(ULocale.ACTUAL_LOCALE))
        val copy = format.copy()
        assertNotSame(format, copy); assertSame(valid, copy.getLocale(null)); assertSame(actual, copy.getLocale(ULocale.ACTUAL_LOCALE))
        format.setLocale(null, null)
        assertNull(format.getLocale(null)); assertSame(valid, copy.getLocale(null))
    }
    @Test fun inheritedFormatProtocol() {
        val format = Sample()
        assertEquals("42", format.format(42))
        val buffer = StringBuilder("pre:"); val position = FormatFieldPosition(0)
        assertSame(buffer, format.format("abc", buffer, position)); assertEquals("pre:abc", buffer.toString())
        assertEquals(4, position.getBeginIndex()); assertEquals(7, position.getEndIndex())
        assertEquals("accepted", format.parseObject("ok!"))
        val failure = assertFailsWith<ParseException> { format.parseObject("no") }
        assertEquals(1, failure.getErrorOffset())
        val iterator = format.formatToCharacterIterator("abc")
        assertEquals('a', iterator.first()); assertEquals(3, iterator.getEndIndex()); assertTrue(iterator.getAllAttributeKeys().isEmpty())
    }
    @Test fun displayContextValues() {
        assertEquals(listOf(0, 1, 0, 1, 2, 3, 4, 0, 1, 0, 1), DisplayContext.entries.map { it.value() })
        assertEquals(5, DisplayContext.entries.count { it.type() == DisplayContext.Type.CAPITALIZATION })
    }
}
