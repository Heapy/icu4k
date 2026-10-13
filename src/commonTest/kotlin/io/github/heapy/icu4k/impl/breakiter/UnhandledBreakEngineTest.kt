package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.text.StringCharacterIterator
import kotlin.test.*

class UnhandledBreakEngineTest {
    @Test fun scriptAccumulationAndCursorSkippingWithoutNewBreaks() {
        val engine = UnhandledBreakEngine()
        assertFalse(engine.handles(65))
        engine.handleChar(65)
        assertTrue(engine.handles(90)); assertFalse(engine.handles(0x3a3))
        val text = StringCharacterIterator("ABΣC")
        val breaks = DictionaryBreakEngine.DequeI().also { it.push(42) }
        assertEquals(0, engine.findBreaks(text, 99, 4, breaks, false))
        assertEquals(2, text.getIndex())
        assertEquals(1, breaks.size()); assertEquals(42, breaks.peek())
        engine.handleChar(0x3a3)
        assertTrue(engine.handles(65)); assertTrue(engine.handles(0x3c3))
        engine.findBreaks(text, 0, 4, breaks, true)
        assertEquals(4, text.getIndex())
    }
}
