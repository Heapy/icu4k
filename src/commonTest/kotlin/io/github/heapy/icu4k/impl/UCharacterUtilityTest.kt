package io.github.heapy.icu4k.impl

import kotlin.test.*

class UCharacterUtilityTest {
    @Test fun unsignedBytesEmptyFieldsAndBoundedSkips() {
        val bytes = byteArrayOf(0, 65, -1, 0, 66, 0)
        val out = StringBuilder("prefix")
        assertEquals(1, UCharacterUtility.getNullTermByteSubString(out, bytes, 0))
        assertEquals("prefix", out.toString())
        assertEquals(4, UCharacterUtility.getNullTermByteSubString(out, bytes, 1))
        assertEquals("prefixAÿ", out.toString())
        assertEquals(3, UCharacterUtility.compareNullTermByteSubString("!Aÿ?", bytes, 1, 1))
        assertEquals(-1, UCharacterUtility.compareNullTermByteSubString("A", bytes, 0, 1))
        assertEquals(4, UCharacterUtility.skipNullTermByteSubString(bytes, 0, 2))
        assertEquals(3, UCharacterUtility.skipByteSubString(bytes, 1, 5, 0))
        assertEquals(2, UCharacterUtility.skipByteSubString(bytes, 1, 2, 0))
        assertEquals(1, UCharacterUtility.skipNullTermByteSubString(bytes, 1, -1))
        assertEquals(0, UCharacterUtility.skipByteSubString(bytes, -1, 0, 0))
        assertFailsWith<IndexOutOfBoundsException> { UCharacterUtility.getNullTermByteSubString(StringBuilder(), byteArrayOf(65), 0) }
        assertEquals(Int.MIN_VALUE or 1, UCharacterUtility.toInt(0x8000.toChar(), 1.toChar()))
        assertTrue(UCharacterUtility.isNonCharacter(0x10ffff))
        assertTrue(UCharacterUtility.isNonCharacter(0xfdd0))
        assertFalse(UCharacterUtility.isNonCharacter(0xfdf0))
    }
}
