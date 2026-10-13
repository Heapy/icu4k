package io.github.heapy.icu4k.impl

import kotlin.test.*

class USerializedSetTest {
    @Test fun handlesBmpSupplementaryAndImplicitEnd() {
        // [A..Z], [U+FFFF..U+10000], [U+10FFFF].
        val raw = intArrayOf(0x8007, 3, 65, 91, 0xffff, 1, 1, 0x10, 0xffff).map { it.toChar() }.toCharArray()
        val set = USerializedSet()
        assertTrue(set.getSet(raw, 0))
        assertEquals(3, set.countRanges())
        assertTrue(set.contains(65)); assertFalse(set.contains(91))
        assertTrue(set.contains(0xffff)); assertTrue(set.contains(0x10000)); assertFalse(set.contains(0x10001))
        val range = IntArray(2)
        assertTrue(set.getRange(2, range)); assertContentEquals(intArrayOf(0x10ffff, 0x10ffff), range)
        assertFalse(set.getRange(3, range))
        raw.fill(0.toChar())
        assertTrue(set.contains(0x10ffff))
    }
    @Test fun singletonAndInvalidInput() {
        for (cp in listOf(0, 0xffff, 0x10000, 0x10ffff)) {
            val set = USerializedSet()
            set.setToOne(cp)
            assertEquals(1, set.countRanges())
            assertTrue(set.contains(cp)); assertFalse(set.contains(cp + 1))
        }
        val set = USerializedSet()
        assertFailsWith<IllegalArgumentException> { set.getRange(0, null) }
        assertFailsWith<IndexOutOfBoundsException> { set.getSet(charArrayOf(4.toChar()), 0) }
        assertEquals(0, set.countRanges())
        assertFalse(set.getRange(-1, null))
    }
}
