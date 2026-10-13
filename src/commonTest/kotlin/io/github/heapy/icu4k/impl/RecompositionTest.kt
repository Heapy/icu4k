package io.github.heapy.icu4k.impl

import kotlin.test.*

class RecompositionTest {
    @Test fun recomposesHangulAndRespectsBlockingAndContiguity() {
        val impl = Normalizer2Impl().load("nfc.nrm")
        fun recompose(text: String, contiguous: Boolean): String {
            val result = StringBuilder(text)
            impl.recompose(Normalizer2Impl.ReorderingBuffer(impl, result, 20), 0, contiguous)
            return result.toString()
        }
        assertEquals("é각", recompose("e\u0301\u1100\u1161\u11a8", false))
        assertEquals("á\u0327", recompose("a\u0327\u0301", false))
        assertEquals("a\u0327\u0301", recompose("a\u0327\u0301", true))
        assertEquals("", recompose("", false))
    }
}
