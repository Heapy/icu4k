package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.test.*

class NormalizationPropertyStartsTest {
    @Test fun leadingClassesAndHangulBoundaries() {
        val impl = Normalizer2Impl().load("nfc.nrm")
        val lccc = UnicodeSet()
        impl.addLcccChars(lccc)
        assertTrue(lccc.contains(0x301))
        assertFalse(lccc.contains(0xe9))
        assertFalse(lccc.contains(0xd800))
        val starts = UnicodeSet().add(0x10ffff)
        impl.addPropertyStarts(starts)
        for (c in 0xac00 until 0xd7a4 step 28) {
            assertTrue(starts.contains(c))
            assertTrue(starts.contains(c + 1))
        }
        assertTrue(starts.contains(0xd7a4))
        assertTrue(starts.contains(0x10ffff))
    }
}
