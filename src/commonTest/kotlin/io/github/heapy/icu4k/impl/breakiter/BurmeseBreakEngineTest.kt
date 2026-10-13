package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.text.StringCharacterIterator
import kotlin.test.*

class BurmeseBreakEngineTest {
    @Test fun embeddedDictionarySegmentsMyanmarOnEveryTarget() {
        val engine = BurmeseBreakEngine()
        assertTrue(engine.handles(0x1000))
        assertFalse(engine.handles(65))
        val text = "မြန်မာဘာသာစကား"
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
        assertEquals(engine, BurmeseBreakEngine())
        assertEquals(engine.hashCode(), BurmeseBreakEngine().hashCode())
    }
}
