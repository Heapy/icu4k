package io.github.heapy.icu4k.impl

import kotlin.test.*

class ComposeTest {
    @Test fun canonicalHangulAndAppendComposition() {
        val impl = Normalizer2Impl().load("nfc.nrm")
        for ((input, expected) in listOf("e\u0301" to "é", "\u1100\u1161\u11a8" to "각", "A\u0301\u0323" to "Ạ\u0301")) {
            val out = StringBuilder()
            assertTrue(impl.compose(input, 0, input.length, false, true, Normalizer2Impl.ReorderingBuffer(impl, out, 16)))
            assertEquals(expected, out.toString())
            assertFalse(impl.compose(input, 0, input.length, false, false, Normalizer2Impl.ReorderingBuffer(impl, StringBuilder(), 16)))
        }
        val out = StringBuilder("e")
        impl.composeAndAppend("\u0301!", true, false, Normalizer2Impl.ReorderingBuffer(impl, out, 16))
        assertEquals("é!", out.toString())
    }
}
