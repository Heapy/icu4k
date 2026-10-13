package io.github.heapy.icu4k.impl

import kotlin.test.*

class ReorderingBufferTest {
    @Test fun ordersMarksAndKeepsStarterBoundaries() {
        val impl = Normalizer2Impl().load("nfc.nrm")
        val text = StringBuilder("A")
        val buffer = Normalizer2Impl.ReorderingBuffer(impl, text, 16)
        buffer.append(0x301, 230)
        buffer.append(0x323, 220)
        assertEquals("A\u0323\u0301", text.toString())
        assertEquals(230, buffer.getLastCC())
        buffer.appendZeroCC('B'.code)
        buffer.append(0x334, 1)
        assertEquals("A\u0323\u0301B\u0334", text.toString())
        buffer.removeSuffix(1)
        assertEquals(0, buffer.getLastCC())
        buffer.flush()
        assertTrue(buffer.equals(text, 0, text.length))
    }
}
