package io.github.heapy.icu4k.impl.breakiter

import com.ibm.icu.impl.breakiter.BurmeseBreakEngine as JavaEngine
import com.ibm.icu.impl.breakiter.DictionaryBreakEngine as JavaDictionary
import io.github.heapy.icu4k.text.StringCharacterIterator
import kotlin.random.Random
import kotlin.test.*

class BurmeseBreakEngineOracleTest {
    @Test fun dictionaryLookaheadUnknownWordsAndMarksMatchOriginal() {
        val j = JavaEngine()
        val k = BurmeseBreakEngine()
        for (cp in 0..0x10ffff) if (j.handles(cp) != k.handles(cp)) fail("handles $cp")
        val random = Random(92841)
        val sources = mutableListOf("", "က", "မြန်မာဘာသာစကား", "မင်္ဂလာပါ", "ကျွန်တော်မြန်မာစာလေ့လာနေပါတယ်", "ကခဂဃငစဆဇဈည", "က က ခ", "က\u1037\u103aခ", "Aမြန်မာ😀ဘာသာB")
        repeat(500) {
            sources += buildString {
                repeat(random.nextInt(2, 70)) {
                    append(if (random.nextInt(8) == 0) ' ' else random.nextInt(0x1000, 0x10a0).toChar())
                }
            }
        }
        for (source in sources) repeat(4) { trial ->
            val start = if (trial == 0) 0 else random.nextInt(source.length + 1)
            val end = if (trial == 0) source.length else random.nextInt(start, source.length + 1)
            for (phrase in listOf(false, true)) {
                val jt = java.text.StringCharacterIterator(source, start)
                val kt = StringCharacterIterator(source, start)
                val jb = JavaDictionary.DequeI(); val kb = DictionaryBreakEngine.DequeI()
                jb.push(-1); kb.push(-1)
                assertEquals(j.findBreaks(jt, start, end, jb, phrase), k.findBreaks(kt, start, end, kb, phrase), "$source $start..$end")
                assertEquals(jt.index, kt.getIndex())
                assertEquals(jb.size(), kb.size())
                for (i in 0 until jb.size()) assertEquals(jb.elementAt(i), kb.elementAt(i))
            }
        }
    }
}
