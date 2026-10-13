package io.github.heapy.icu4k.impl

import kotlin.test.*

class StringDecompositionTest {
    @Test fun decomposesAndOrdersWholeStringsAndAppendsAtBoundary() {
        val impl = Normalizer2Impl().load("nfc.nrm")
        val text = StringBuilder("old")
        impl.decompose("é\u0323각", 0, 3, text, -1)
        assertEquals("e\u0323\u0301\u1100\u1161\u11a8", text.toString())
        val buffer = Normalizer2Impl.ReorderingBuffer(impl, StringBuilder("A\u0301"), 10)
        impl.decomposeAndAppend("\u0323", false, buffer)
        assertEquals("A\u0323\u0301", buffer.getStringBuilder().toString())
        assertEquals(0, impl.decompose("é", 0, 1, null))
        assertEquals(2, impl.decompose("e\u0301", 0, 2, null))
    }
}
