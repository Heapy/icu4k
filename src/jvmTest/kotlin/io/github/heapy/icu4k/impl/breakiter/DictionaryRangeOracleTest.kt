package com.ibm.icu.impl.breakiter

import com.ibm.icu.text.UnicodeSet as JavaSet
import io.github.heapy.icu4k.impl.breakiter.DictionaryBreakEngine as CommonEngine
import io.github.heapy.icu4k.text.CharacterIterator as CommonCursor
import io.github.heapy.icu4k.text.StringCharacterIterator as CommonStringCursor
import io.github.heapy.icu4k.text.UnicodeSet as CommonSet
import java.text.CharacterIterator
import java.text.StringCharacterIterator
import kotlin.random.Random
import kotlin.test.*

class DictionaryRangeOracleTest {
    @Test fun rangeDispatchCursorRestorationAndSetCopiesMatch() {
        val random = Random(777)
        repeat(500) {
            var ja = emptyList<Any>(); var ka = emptyList<Any>()
            val j = object : DictionaryBreakEngine() {
                override fun divideUpDictionaryRange(text: CharacterIterator, rangeStart: Int, rangeEnd: Int, foundBreaks: DequeI, isPhraseBreaking: Boolean): Int {
                    ja = listOf(rangeStart, rangeEnd, text.index, isPhraseBreaking)
                    foundBreaks.push(rangeStart); foundBreaks.push(rangeEnd)
                    text.first()
                    return rangeEnd - rangeStart
                }
            }
            val k = object : CommonEngine() {
                override fun divideUpDictionaryRange(text: CommonCursor, rangeStart: Int, rangeEnd: Int, foundBreaks: DequeI, isPhraseBreaking: Boolean): Int {
                    ka = listOf(rangeStart, rangeEnd, text.getIndex(), isPhraseBreaking)
                    foundBreaks.push(rangeStart); foundBreaks.push(rangeEnd)
                    text.first()
                    return rangeEnd - rangeStart
                }
            }
            val js = JavaSet().add(65, 90).add(0xd800, 0xdfff).add(0x10000, 0x10ffff)
            val ks = CommonSet().add(65, 90).add(0xd800, 0xdfff).add(0x10000, 0x10ffff)
            j.setCharacters(js); k.setCharacters(ks)
            js.clear(); ks.clear()
            for (cp in listOf(0, 65, 90, 91, 0xffff, 0x10000, 0x10ffff)) assertEquals(j.handles(cp), k.handles(cp))
            val text = CharArray(30) { when (random.nextInt(4)) { 0 -> 'A'; 1 -> ' '; else -> random.nextInt(0xd800, 0xe000).toChar() } }.concatToString()
            val begin = random.nextInt(10); val end = random.nextInt(begin, 31)
            val index = random.nextInt(begin, end + 1)
            val ji = StringCharacterIterator(text, begin, end, index)
            val ki = CommonStringCursor(text, begin, end, index)
            val jb = DictionaryBreakEngine.DequeI().apply { push(-99) }
            val kb = CommonEngine.DequeI().apply { push(-99) }
            val phrase = random.nextBoolean()
            // startPos is intentionally independent of the iterator cursor, as upstream ignores it.
            assertEquals(j.findBreaks(ji, 0, end, jb, phrase), k.findBreaks(ki, 0, end, kb, phrase))
            assertEquals(ja, ka); assertEquals(ji.index, ki.getIndex())
            assertEquals(jb.size(), kb.size())
            for (i in 0 until jb.size()) assertEquals(jb.elementAt(i), kb.elementAt(i))
        }
    }
}
