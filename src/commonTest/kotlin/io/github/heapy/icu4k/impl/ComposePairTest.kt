package io.github.heapy.icu4k.impl

import kotlin.test.*

class ComposePairTest {
    @Test fun composesTablePairsAndHangul() {
        val impl = Normalizer2Impl().load("nfc.nrm")
        assertEquals(0xe9, impl.composePair('e'.code, 0x301))
        assertEquals(0xac00, impl.composePair(0x1100, 0x1161))
        assertEquals(0xac01, impl.composePair(0xac00, 0x11a8))
        assertEquals(-1, impl.composePair(0xac00, 0x11a7))
        assertEquals(-1, impl.composePair('A'.code, -1))
        assertEquals(-1, impl.composePair(-1, 0x301))
    }
}
