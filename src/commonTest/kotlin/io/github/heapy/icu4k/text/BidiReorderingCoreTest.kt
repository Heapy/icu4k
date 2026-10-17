// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*

class BidiReorderingCoreTest {
    @Test fun completeUnicodeSourceDirectionFingerprint() {
        var hash = -3750763034362895579L
        for (c in 0..0x10ffff) {
            val text = UTF16.valueOf(c)
            hash = (hash xor BidiReorderingCore.baseDirection(text).toLong()) * 1099511628211L
            val chars = text.toCharArray()
            hash = (hash xor if (BidiReorderingCore.requiresBidi(chars, 0, chars.size)) 1L else 0L) * 1099511628211L
        }
        assertEquals(-6620136635670834878L, hash)
        assertEquals(3.toByte(), BidiReorderingCore.baseDirection(null))
        assertEquals(1.toByte(), BidiReorderingCore.baseDirection("𞤀"))
        assertFalse(BidiReorderingCore.requiresBidi("𞤀".toCharArray(), 0, 2))
        assertFalse(BidiReorderingCore.requiresBidi(null, 0, 0))
        assertTrue(BidiReorderingCore.requiresBidi("א".toCharArray(), 0, 20))
        assertFailsWith<IndexOutOfBoundsException> { BidiReorderingCore.requiresBidi("A".toCharArray(), 0, 20) }
    }
    @Test fun reorderingMapsPreserveLevelsAndSourceDuplicateQuirk() {
        val levels = byteArrayOf(0, 1, 2, 2, 1, 0)
        val saved = levels.copyOf()
        assertContentEquals(intArrayOf(0, 4, 2, 3, 1, 5), BidiReorderingCore.reorderVisual(levels))
        assertContentEquals(intArrayOf(0, 4, 2, 3, 1, 5), BidiReorderingCore.reorderLogical(levels))
        assertContentEquals(saved, levels)
        assertContentEquals(intArrayOf(0, 0, 2), BidiReorderingCore.invertMap(intArrayOf(2, 2, 2)))
        assertContentEquals(intArrayOf(-1, 2, -1, 0), BidiReorderingCore.invertMap(intArrayOf(3, -1, 1)))
        assertNull(BidiReorderingCore.invertMap(null))
        assertNull(BidiReorderingCore.reorderLogical(null))
        assertNull(BidiReorderingCore.reorderVisual(byteArrayOf()))
        assertNull(BidiReorderingCore.reorderVisual(byteArrayOf(127)))
        assertNull(BidiReorderingCore.reorderLogical(byteArrayOf(-128)))
        val objects = arrayOf("prefix", "a", "b", "c", "d", "e", "f", "suffix")
        BidiReorderingCore.reorderVisually(levels, 0, objects, 1, 6)
        assertContentEquals(arrayOf("prefix", "a", "e", "c", "d", "b", "f", "suffix"), objects)
    }
}
