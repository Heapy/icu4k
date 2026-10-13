package io.github.heapy.icu4k.text

import kotlin.test.*

class UTF16ComparatorTest {
    @Test fun configurableUnicodeOrderingAndFolding() {
        val comparator = UTF16.StringComparator()
        assertEquals(0, comparator.compare(null, null))
        assertEquals(-1, comparator.compare(null, ""))
        assertTrue(comparator.compare("😀", "\ue000") < 0)
        comparator.setCodePointCompare(true)
        assertTrue(comparator.compare("😀", "\ue000") > 0)
        comparator.setIgnoreCase(true, 0)
        assertEquals(0, comparator.compare("Straße", "STRASSE"))
        assertTrue(comparator.compare("é", "e\u0301") != 0)
        comparator.setIgnoreCase(true, 1)
        assertEquals(0, comparator.compare("I", "ı"))
        assertFailsWith<IllegalArgumentException> { comparator.setIgnoreCase(false, 2) }
        assertFalse(comparator.getIgnoreCase())
        assertEquals(1, comparator.getIgnoreCaseOption())
    }
}
