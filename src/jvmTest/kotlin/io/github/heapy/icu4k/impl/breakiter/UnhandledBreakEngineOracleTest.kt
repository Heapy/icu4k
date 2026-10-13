package io.github.heapy.icu4k.impl.breakiter

import com.ibm.icu.impl.breakiter.UnhandledBreakEngine as JavaEngine
import com.ibm.icu.impl.breakiter.DictionaryBreakEngine as JavaDictionary
import io.github.heapy.icu4k.text.StringCharacterIterator
import kotlin.test.*

class UnhandledBreakEngineOracleTest {
    @Test fun handledScriptsAndSkippingMatchOriginal() {
        val j = JavaEngine(); val k = UnhandledBreakEngine()
        for (c in listOf(65, 0x3a3, 0x10400, 0x4e00, 65)) { j.handleChar(c); k.handleChar(c) }
        for (c in 0..0x10ffff) if (j.handles(c) != k.handles(c)) fail("handles $c")
        for (source in listOf("", "ABΣC", "A😀B", "𐐀𐐨!A", "中文AΣ", CharArray(2) { if (it == 0) 0xd800.toChar() else 65.toChar() }.concatToString())) {
            for (start in 0..source.length) for (end in start..source.length) for (phrase in listOf(false, true)) {
                val jt = java.text.StringCharacterIterator(source, start)
                val kt = StringCharacterIterator(source, start)
                val jb = JavaDictionary.DequeI(); val kb = DictionaryBreakEngine.DequeI()
                jb.push(42); kb.push(42)
                assertEquals(j.findBreaks(jt, -1, end, jb, phrase), k.findBreaks(kt, -1, end, kb, phrase))
                assertEquals(jt.index, kt.getIndex(), "$source $start $end")
                assertEquals(jb.size(), kb.size()); assertEquals(jb.peek(), kb.peek())
            }
        }
    }
    @Test fun aSingleWriterCanPublishWhileReadersRun() {
        val engine = UnhandledBreakEngine()
        val pool = java.util.concurrent.Executors.newFixedThreadPool(5)
        val gate = java.util.concurrent.CyclicBarrier(5)
        try {
            val writer = pool.submit {
                gate.await()
                for (cp in listOf(65, 0x3a3, 0x10400, 0x4e00, 0x621)) engine.handleChar(cp)
            }
            val readers = (0 until 4).map {
                pool.submit {
                    gate.await()
                    repeat(2000) {
                        engine.handles(65)
                        val text = StringCharacterIterator("AAΣ中文")
                        assertEquals(0, engine.findBreaks(text, 0, 5, DictionaryBreakEngine.DequeI(), false))
                        assertTrue(text.getIndex() in 0..5)
                    }
                }
            }
            writer.get(20, java.util.concurrent.TimeUnit.SECONDS)
            readers.forEach { it.get(20, java.util.concurrent.TimeUnit.SECONDS) }
            for (cp in listOf(65, 0x3a3, 0x10400, 0x4e00, 0x621)) assertTrue(engine.handles(cp))
        } finally { pool.shutdownNow() }
    }
}
