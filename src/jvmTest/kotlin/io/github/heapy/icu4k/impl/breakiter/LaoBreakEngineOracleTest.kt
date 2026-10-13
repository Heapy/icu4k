package io.github.heapy.icu4k.impl.breakiter

import com.ibm.icu.impl.breakiter.LaoBreakEngine as JavaEngine
import com.ibm.icu.impl.breakiter.DictionaryBreakEngine as JavaDictionary
import io.github.heapy.icu4k.text.StringCharacterIterator
import kotlin.random.Random
import kotlin.test.*

class LaoBreakEngineOracleTest {
    @Test fun dictionaryLookaheadUnknownWordsAndMarksMatchOriginal() {
        val j = JavaEngine()
        val k = LaoBreakEngine()
        for (cp in 0..0x10ffff) if (j.handles(cp) != k.handles(cp)) fail("handles $cp")
        val random = Random(92842)
        val sources = mutableListOf("", "ກ", "ພາສາລາວ", "ສະບາຍດີ", "ປະເທດລາວ", "ກຂຄງຈສຊຍ", "ກ ກ ຂ", "ເກັ່ງ", "Aພາສາ😀ລາວB")
        repeat(500) {
            sources += buildString {
                repeat(random.nextInt(2, 70)) {
                    append(if (random.nextInt(8) == 0) ' ' else random.nextInt(0x0e80, 0x0f00).toChar())
                }
            }
        }
        for (source in sources) repeat(4) { trial ->
            val start = if (trial == 0) 0 else random.nextInt(source.length + 1)
            val end = if (trial == 0) source.length else random.nextInt(start, source.length + 1)
            for (phrase in listOf(false, true)) for (direct in listOf(false, true)) {
                val jt = java.text.StringCharacterIterator(source, start, end, start)
                val kt = StringCharacterIterator(source, start, end, start)
                val jb = JavaDictionary.DequeI(); val kb = DictionaryBreakEngine.DequeI()
                jb.push(-1); kb.push(-1)
                val expected = if (direct) j.divideUpDictionaryRange(jt, start, end, jb, phrase)
                    else j.findBreaks(jt, start, end, jb, phrase)
                val actual = if (direct) k.divideUpDictionaryRange(kt, start, end, kb, phrase)
                    else k.findBreaks(kt, start, end, kb, phrase)
                assertEquals(expected, actual, "$source $start..$end direct=$direct")
                assertEquals(jt.index, kt.getIndex())
                assertEquals(jb.size(), kb.size())
                for (i in 0 until jb.size()) assertEquals(jb.elementAt(i), kb.elementAt(i))
            }
        }
    }
}
