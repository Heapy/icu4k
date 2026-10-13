package io.github.heapy.icu4k.impl

import kotlin.test.*

class MakeFCDTest {
    @Test fun repairsOnlyUnsafeSegmentsAndMergesAppendBoundary() {
        val impl = Normalizer2Impl().load("nfc.nrm")
        val result = StringBuilder()
        val buffer = Normalizer2Impl.ReorderingBuffer(impl, result, 20)
        val text = "é\u0323!é"
        assertEquals(text.length, impl.makeFCD(text, 0, text.length, buffer))
        assertEquals("e\u0323\u0301!é", result.toString())
        assertEquals(0, impl.makeFCD("é\u0323", 0, 2, null))
        val appended = StringBuilder("é")
        impl.makeFCDAndAppend("\u0323", false, Normalizer2Impl.ReorderingBuffer(impl, appended, 10))
        assertEquals("e\u0323\u0301", appended.toString())
    }
}
