package io.github.heapy.icu4k.impl

import kotlin.test.*

class CompositionQuickCheckTest {
    @Test fun packedResultDistinguishesYesMaybeAndNo() {
        val impl = Normalizer2Impl().load("nfc.nrm")
        assertEquals(6, impl.composeQuickCheck("abc", 0, 3, false, false))
        assertEquals(5, impl.composeQuickCheck("e\u0301", 0, 2, false, false))
        assertEquals(0, impl.composeQuickCheck("e\u0301", 0, 2, false, true))
        assertEquals(0, impl.composeQuickCheck("\u0340", 0, 1, false, false))
        assertEquals(0, impl.composeQuickCheck("", 0, 0, false, true))
    }
}
