package io.github.heapy.icu4k.text

import kotlin.test.*

class UTF16SubstringTest {
    @Test fun searchesAndReplacesWholeUtf16Sequences() {
        val source = "A😀B😀C"
        assertEquals(1, UTF16.indexOf(source, "😀"))
        assertEquals(4, UTF16.lastIndexOf(source, "😀"))
        assertEquals("A!B!C", UTF16.replace(source, "😀", "!"))
        val trail = charArrayOf(0xde00.toChar()).concatToString()
        assertEquals(-1, UTF16.indexOf(source, trail))
        assertEquals(-1, UTF16.lastIndexOf(source, trail))
        assertFailsWith<IndexOutOfBoundsException> { UTF16.indexOf(source, "") }
        val lead = charArrayOf(0xd83d.toChar()).concatToString()
        // ICU's no-offset overload looks one unit farther than the explicit-offset overload.
        assertEquals(0, UTF16.indexOf("😀A", lead))
        assertEquals(-1, UTF16.indexOf("😀A", lead, 0))
        assertFailsWith<IndexOutOfBoundsException> { UTF16.indexOf("😀", lead) }
    }
}
