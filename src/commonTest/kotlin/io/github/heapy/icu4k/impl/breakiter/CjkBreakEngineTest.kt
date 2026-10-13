package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.text.StringCharacterIterator
import kotlin.test.*

class CjkBreakEngineTest {
    @Test fun dictionarySegmentationAndEngineIdentity() {
        val cj = CjkBreakEngine(false)
        assertTrue(cj.handles('日'.code)); assertTrue(cj.handles('カ'.code))
        assertFalse(cj.handles('한'.code))
        assertTrue(CjkBreakEngine(true).handles('한'.code))
        assertEquals(cj, CjkBreakEngine(false))
        assertNotEquals(cj, CjkBreakEngine(true))
        for (source in listOf("東京都に行きます", "中文分词测试", "ｶﾞｯｺｳ日本語")) {
            val cursor = StringCharacterIterator(source)
            val breaks = DictionaryBreakEngine.DequeI()
            val count = cj.findBreaks(cursor, 0, source.length, breaks, false)
            assertEquals(source.length, cursor.getIndex())
            assertEquals(count, breaks.size())
            assertTrue(count > 0)
            for (i in 0 until breaks.size()) assertTrue(breaks.elementAt(i) in 1 until source.length)
        }
    }
}
