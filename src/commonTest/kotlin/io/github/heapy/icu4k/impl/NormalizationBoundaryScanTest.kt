package io.github.heapy.icu4k.impl

import kotlin.test.*

class NormalizationBoundaryScanTest {
    @Test fun scansToStarterBoundariesAndDecomposesShortSegments() {
        val impl = Normalizer2Impl().load("nfc.nrm")
        val text = "\u0301\u0323A"
        assertEquals(2, impl.findNextCompBoundary(text, 0, text.length, false))
        assertEquals(2, impl.findNextFCDBoundary(text, 0, text.length))
        val result = StringBuilder()
        val buffer = Normalizer2Impl.ReorderingBuffer(impl, result, 10)
        assertEquals(2, impl.decomposeShort(text, 0, text.length, true, false, buffer))
        assertEquals("\u0323\u0301", result.toString())
    }
}
