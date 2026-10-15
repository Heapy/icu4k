// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import kotlin.test.*

class CollationFCDTest {
    @Test fun exactBmpAndUnicodeFingerprintsFromIcu4j() {
        var leading = 0; var trailing = 0; var bmpHash = 1
        for (c in 0..0xffff) {
            val l = CollationFCD.hasLccc(c); val t = CollationFCD.hasTccc(c)
            if (l) ++leading
            if (t) ++trailing
            bmpHash = 31 * bmpHash + (if (l) 1 else 0) + (if (t) 2 else 0)
        }
        assertEquals(1777, leading)
        assertEquals(1725, trailing)
        assertEquals(-906383230, bmpHash)
        var may = 0; var tibetan = 0; var unicodeHash = 1
        for (c in 0..0x10ffff) {
            val l = CollationFCD.mayHaveLccc(c); val t = CollationFCD.maybeTibetanCompositeVowel(c)
            if (l) ++may
            if (t) ++tibetan
            unicodeHash = 31 * unicodeHash + (if (l) 1 else 0) + (if (t) 2 else 0)
        }
        assertEquals(17137, may)
        assertEquals(128, tibetan)
        assertEquals(2127947816, unicodeHash)
    }
    @Test fun conservativeSurrogateFlagsAndTibetanSpecials() {
        assertFalse(CollationFCD.hasLccc('A'.code))
        assertFalse(CollationFCD.hasLccc(0xc0))
        assertTrue(CollationFCD.hasTccc(0xc0))
        assertTrue(CollationFCD.hasLccc(0x301))
        assertTrue(CollationFCD.hasTccc(0x301))
        assertFalse(CollationFCD.hasLccc(0x34f))
        assertFalse(CollationFCD.hasTccc(0x34f))
        assertTrue(CollationFCD.hasLccc(0xdc00))
        assertFalse(CollationFCD.hasTccc(0xdc00))
        // Linear B U+10000 is FCD-inert, but its surrogate block contains non-starters.
        assertTrue(CollationFCD.mayHaveLccc(0x10000))
        for (c in listOf(0xf73, 0xf75, 0xf81)) assertTrue(CollationFCD.maybeTibetanCompositeVowel(c))
        assertTrue(CollationFCD.maybeTibetanCompositeVowel(0xf01)) // Source's deliberately imprecise check.
        assertTrue(CollationFCD.isFCD16OfTibetanCompositeVowel(0x8182))
        assertTrue(CollationFCD.isFCD16OfTibetanCompositeVowel(0x8184))
        assertFalse(CollationFCD.isFCD16OfTibetanCompositeVowel(0x18182))
        for (c in listOf(Int.MIN_VALUE, -1)) {
            assertFalse(CollationFCD.hasLccc(c))
            assertFalse(CollationFCD.hasTccc(c))
            assertFalse(CollationFCD.mayHaveLccc(c))
        }
        if (collationFCDAssertionsEnabled()) {
            assertFailsWith<AssertionError> { CollationFCD.hasLccc(0x10000) }
            assertFailsWith<AssertionError> { CollationFCD.hasTccc(0x10000) }
        } else {
            assertFailsWith<IndexOutOfBoundsException> { CollationFCD.hasLccc(0x10000) }
            assertFailsWith<IndexOutOfBoundsException> { CollationFCD.hasTccc(0x10000) }
        }
    }
}
