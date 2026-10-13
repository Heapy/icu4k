package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.text.StringCharacterIterator
import kotlin.test.*

class LaoBreakEngineTest {
    @Test fun embeddedDictionarySegmentsLaoOnEveryTarget() {
        val engine = LaoBreakEngine()
        assertTrue(engine.handles(0x0e81))
        assertFalse(engine.handles(65))
        val text = "ພາສາລາວ"
        val cursor = StringCharacterIterator(text)
        val breaks = DictionaryBreakEngine.DequeI()
        breaks.push(-1)
        val count = engine.findBreaks(cursor, 0, text.length, breaks, false)
        assertEquals(text.length, cursor.getIndex())
        assertEquals(breaks.size() - 1, count)
        assertTrue(count > 0)
        var previous = 0
        for (i in 1 until breaks.size()) {
            val boundary = breaks.elementAt(i)
            assertTrue(boundary > previous && boundary < text.length)
            previous = boundary
        }
        assertEquals(engine, LaoBreakEngine())
        assertEquals(engine.hashCode(), LaoBreakEngine().hashCode())
    }
}
