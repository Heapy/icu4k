// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import kotlin.test.*

class CollationTest {
    @Test fun compactElementsAndTags() {
        assertEquals(0xabcd000012003400uL.toLong(), Collation.ceFromSimpleCE32(0xabcd1234.toInt()))
        assertEquals(0xabcdef0005000500uL.toLong(), Collation.ceFromCE32(0xabcdefc1.toInt()))
        assertEquals(0x12345600L, Collation.ceFromCE32(0x123456c2))
        assertEquals(0xce00000005001200uL.toLong(), Collation.latinCE0FromCE32(0xce1256c4.toInt()))
        assertEquals(0x56000500L, Collation.latinCE1FromCE32(0xce1256c4.toInt()))
        for (tag in 0..15) {
            val ce32 = Collation.makeCE32FromTagIndexAndLength(tag, Collation.MAX_INDEX, 31)
            assertTrue(Collation.isSpecialCE32(ce32))
            assertEquals(tag, Collation.tagFromCE32(ce32))
            assertEquals(Collation.MAX_INDEX, Collation.indexFromCE32(ce32))
            assertEquals(31, Collation.lengthFromCE32(ce32))
            assertEquals(tag == 8 || tag == 9, Collation.ce32HasContext(ce32))
            assertEquals(tag == 1 || tag == 2 || tag == 4, Collation.isSelfContainedCE32(ce32))
        }
        assertFalse(Collation.isAssignedCE32(Collation.FALLBACK_CE32))
        assertFalse(Collation.isAssignedCE32(Collation.UNASSIGNED_CE32))
        assertTrue(Collation.isAssignedCE32(Collation.NO_CE32))
        assertEquals(9.toChar(), Collation.digitFromCE32(Collation.makeCE32FromTagIndexAndLength(Collation.DIGIT_TAG, 123, 9)))
    }
    @Test fun primaryCarryBorrowAndOffsetData() {
        assertEquals(0x13040000L, Collation.incTwoBytePrimaryByOffset(0x12fe0000L, true, 1))
        assertEquals(0x13020000L, Collation.incTwoBytePrimaryByOffset(0x12ff0000L, false, 1))
        assertEquals(0x13040200L, Collation.incThreeBytePrimaryByOffset(0x12feff00L, true, 1))
        assertEquals(0x12feff00L, Collation.decThreeBytePrimaryByOneStep(0x13040200L, true, 1))
        assertEquals(0x12ffff00L, Collation.decThreeBytePrimaryByOneStep(0x13020200L, false, 1))
        for (compressed in listOf(false, true)) for (step in 1..127) {
            val base = if (compressed) 0x12040200L else 0x12020200L
            val increased = Collation.incThreeBytePrimaryByOffset(base, compressed, step)
            assertEquals(base, Collation.decThreeBytePrimaryByOneStep(increased, compressed, step))
            val twoByteBase = base and 0xffff0000L
            assertEquals(twoByteBase, Collation.decTwoBytePrimaryByOneStep(Collation.incTwoBytePrimaryByOffset(twoByteBase, compressed, step), compressed, step))
            val data = (base shl 32) or (100L shl 8) or (if (compressed) 0x80L else 0L) or step.toLong()
            assertEquals(increased, Collation.getThreeBytePrimaryForOffsetData(101, data))
        }
    }
    @Test fun implicitPrimariesAndCeLayout() {
        assertEquals(0xfe040202L, Collation.unassignedPrimaryFromCodePoint(-1))
        assertEquals(0xfe040210L, Collation.unassignedPrimaryFromCodePoint(0))
        assertEquals(0xfe040302L, Collation.unassignedPrimaryFromCodePoint(17))
        assertEquals(0xfe050202L, Collation.unassignedPrimaryFromCodePoint(18 * 254 - 1))
        var previous = 0L
        for (c in -1..0x10ffff step 113) {
            val primary = Collation.unassignedPrimaryFromCodePoint(c)
            assertTrue(primary > previous)
            assertEquals(primary shl 32 or 0x05000500L, Collation.unassignedCEFromCodePoint(c))
            previous = primary
        }
        assertEquals(0x1234567805000500L, Collation.makeCE(0x12345678L))
        assertEquals(0x12345678123456c0L, Collation.makeCE(0x12345678L, 0x1234, 0x5600, 3))
    }
}
