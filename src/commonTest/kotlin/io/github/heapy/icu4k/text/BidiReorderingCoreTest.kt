// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*

class BidiReorderingCoreTest {
    private fun checkDirectionPlane(plane: Int, expected: Long) {
        var hash = -3750763034362895579L
        for (c in (plane shl 16) until ((plane + 1) shl 16)) {
            val text = UTF16.valueOf(c)
            hash = (hash xor BidiReorderingCore.baseDirection(text).toLong()) * 1099511628211L
            val chars = text.toCharArray()
            hash = (hash xor if (BidiReorderingCore.requiresBidi(chars, 0, chars.size)) 1L else 0L) * 1099511628211L
        }
        assertEquals(expected, hash, "Unicode plane $plane")
    }
    // Independent ICU4J 78.3 fingerprints; every U+0000..U+10FFFF value remains covered.
    @Test fun directionPlane00() = checkDirectionPlane(0, 3671676255597920665L)
    @Test fun directionPlane01() = checkDirectionPlane(1, -2355836196400009242L)
    @Test fun directionPlane02() = checkDirectionPlane(2, 7440665463651892365L)
    @Test fun directionPlane03() = checkDirectionPlane(3, 7440665463651892365L)
    @Test fun directionPlane04() = checkDirectionPlane(4, 7440665463651892365L)
    @Test fun directionPlane05() = checkDirectionPlane(5, 7440665463651892365L)
    @Test fun directionPlane06() = checkDirectionPlane(6, 7440665463651892365L)
    @Test fun directionPlane07() = checkDirectionPlane(7, 7440665463651892365L)
    @Test fun directionPlane08() = checkDirectionPlane(8, 7440665463651892365L)
    @Test fun directionPlane09() = checkDirectionPlane(9, 7440665463651892365L)
    @Test fun directionPlane10() = checkDirectionPlane(10, 7440665463651892365L)
    @Test fun directionPlane11() = checkDirectionPlane(11, 7440665463651892365L)
    @Test fun directionPlane12() = checkDirectionPlane(12, 7440665463651892365L)
    @Test fun directionPlane13() = checkDirectionPlane(13, 7440665463651892365L)
    @Test fun directionPlane14() = checkDirectionPlane(14, 1999818466053209229L)
    @Test fun directionPlane15() = checkDirectionPlane(15, 7440665463651892365L)
    @Test fun directionPlane16() = checkDirectionPlane(16, 7440665463651892365L)
    @Test fun directionHelperBoundaryBehavior() {
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
