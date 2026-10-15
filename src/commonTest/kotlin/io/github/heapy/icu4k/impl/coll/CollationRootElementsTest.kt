// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import kotlin.test.*

class CollationRootElementsTest {
    @Test fun boundariesAndCallerArrayAreNotCopiedOrCached() {
        val data = rootElementsFixture()
        val root = CollationRootElements(data)
        assertEquals(0x3c00, root.getTertiaryBoundary())
        assertEquals(0x4500, root.getLastCommonSecondary())
        assertEquals(0x8000, root.getSecondaryBoundary())
        assertEquals(0x3d02L, root.getFirstTertiaryCE())
        assertEquals(0x3e02L, root.getLastTertiaryCE())
        assertEquals(0x82000500L, root.getFirstSecondaryCE())
        assertEquals(0x84000500L, root.getLastSecondaryCE())
        assertEquals(0x11040000L, root.getFirstPrimary())
        assertEquals(0x1104000005000500L, root.getFirstPrimaryCE())
        data[4] = 0x22550011L
        data[0] = 6
        assertEquals(0x1100, root.getTertiaryBoundary())
        assertEquals(0x5500, root.getSecondaryBoundary())
        assertEquals(0x2200, root.getLastCommonSecondary())
        assertEquals(0x3e02L, root.getFirstTertiaryCE())
    }
    @Test fun explicitPrimariesAndTwoAndThreeByteRanges() {
        val root = CollationRootElements(rootElementsFixture())
        val primaries = longArrayOf(0x11040000, 0x12040000, 0x12050000, 0x12060000, 0x12070000, 0x13040200, 0x13040400, 0x13040600, 0x13040800, 0x14040000)
        val indexes = intArrayOf(10, 15, 15, 15, 16, 17, 17, 17, 18, 19)
        for (i in primaries.indices) {
            val p = primaries[i]
            val compressible = (p ushr 24) == 0x12L
            assertEquals(indexes[i], root.findPrimary(p))
            assertEquals(if (i + 1 == primaries.size) CollationRootElements.PRIMARY_SENTINEL else primaries[i + 1], root.getPrimaryAfter(p, indexes[i], compressible))
            if (i > 0) assertEquals(primaries[i - 1], root.getPrimaryBefore(p, compressible))
        }
        assertEquals(0x1104000006000500L, root.lastCEWithPrimaryBefore(0x12040000))
        assertEquals(0x1104000006000500L, root.lastCEWithPrimaryBefore(0x12030000))
        assertEquals(0x1204000005000500L, root.firstCEWithPrimaryAtLeast(0x12030000))
        assertEquals(0x1304080005000500L, root.lastCEWithPrimaryBefore(0x14040000))
        assertEquals(0x1404000005000500L, root.firstCEWithPrimaryAtLeast(0x13040900))
    }
    @Test fun explicitAndImpliedSecondaryTertiaryWeights() {
        val root = CollationRootElements(rootElementsFixture())
        assertEquals(0, root.getSecondaryBefore(0, 0x8200))
        assertEquals(0x8200, root.getSecondaryBefore(0, 0x8400))
        assertEquals(0x8400, root.getSecondaryAfter(0, 0x8200))
        assertEquals(0x10000, root.getSecondaryAfter(0, 0x8400))
        assertEquals(0, root.getTertiaryBefore(0, 0, 0x3d02))
        assertEquals(0x3d02, root.getTertiaryBefore(0, 0, 0x3e02))
        assertEquals(0x3e02, root.getTertiaryAfter(0, 0, 0x3d02))
        assertEquals(0x4000, root.getTertiaryAfter(0, 0, 0x3e02))
        assertEquals(0x0500, root.getTertiaryBefore(0, 0x8200, 0x0600))
        assertEquals(0x0600, root.getTertiaryAfter(0, 0x8200, 0x0500))
        assertEquals(0x0100, root.getSecondaryBefore(0x11040000, 0x0400))
        assertEquals(0x0400, root.getSecondaryBefore(0x11040000, 0x0500))
        assertEquals(0x0500, root.getSecondaryBefore(0x11040000, 0x0600))
        assertEquals(0x0500, root.getSecondaryAfter(10, 0x0400))
        assertEquals(0x0600, root.getSecondaryAfter(10, 0x0500))
        assertEquals(0x8000, root.getSecondaryAfter(10, 0x0600))
        assertEquals(0x0500, root.getTertiaryBefore(0x11040000, 0x0500, 0x0600))
        assertEquals(0x0600, root.getTertiaryAfter(10, 0x0500, 0x0500))
        assertEquals(0x3c00, root.getTertiaryAfter(10, 0x0600, 0x0500))
        assertEquals(0x0500, root.getSecondaryBefore(0x14040000, 0x0600))
        assertEquals(0x0600, root.getSecondaryAfter(19, 0x0500))
        assertEquals(0x0100, root.getTertiaryBefore(0x14040000, 0x0500, 0x0500))
        assertEquals(0x3c00, root.getTertiaryAfter(19, 0x0500, 0x0500))
    }
    @Test fun nullConstructionZeroShortcutsBoundsAndLazyAssertions() {
        val absent = CollationRootElements(null)
        assertEquals(0L, absent.firstCEWithPrimaryAtLeast(0))
        assertEquals(0L, absent.lastCEWithPrimaryBefore(0))
        assertFailsWith<NullPointerException> { absent.getTertiaryBoundary() }
        assertFailsWith<IndexOutOfBoundsException> { CollationRootElements(longArrayOf()).getFirstPrimary() }
        val sparse = CollationRootElements(longArrayOf(0))
        if (collationRootElementsAssertionsEnabled()) {
            assertFailsWith<IndexOutOfBoundsException> { sparse.getPrimaryAfter(0, -1, false) }
        } else {
            // Disabled Java assertions never evaluate elements[-1] in the precondition.
            assertEquals(0L, sparse.getPrimaryAfter(0, -1, false))
        }
    }
}

internal fun rootElementsFixture(): LongArray = longArrayOf(
    5, 7, 10, 0x05000500, 0x4580003c,
    0x3d82, 0x3e82,
    0x82000580, 0x82000680, 0x84000580,
    0x11040000, 0x04000480, 0x05000580, 0x05000680, 0x06000580,
    0x12040000, 0x12070001,
    0x13040200, 0x13040802,
    0x14040000, 0x06000680,
    CollationRootElements.PRIMARY_SENTINEL
)
