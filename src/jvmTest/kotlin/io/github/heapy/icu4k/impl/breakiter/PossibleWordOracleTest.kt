package com.ibm.icu.impl.breakiter

import io.github.heapy.icu4k.impl.breakiter.DictionaryBreakEngine as CommonEngine
import io.github.heapy.icu4k.impl.breakiter.DictionaryMatcher as CommonMatcher
import io.github.heapy.icu4k.text.CharacterIterator as CommonIterator
import io.github.heapy.icu4k.text.StringCharacterIterator as CommonStringIterator
import java.text.CharacterIterator
import java.text.StringCharacterIterator
import kotlin.test.*

class PossibleWordOracleTest {
    @Test fun candidateCachingAndBacktrackingMatchIcu() {
        var javaCalls = 0
        var commonCalls = 0
        val jm = object : DictionaryMatcher() {
            override fun getType() = 0
            override fun matches(text: CharacterIterator, maxLength: Int, lengths: IntArray, count: IntArray, limit: Int, values: IntArray?): Int {
                ++javaCalls
                val offset = text.index
                count[0] = if (offset % 2 == 0) 3 else 0
                lengths[0] = 1; lengths[1] = 2; lengths[2] = 3
                text.setIndex(offset + 4)
                return 4
            }
        }
        val km = object : CommonMatcher() {
            override fun getType() = 0
            override fun matches(text: CommonIterator, maxLength: Int, lengths: IntArray, count: IntArray, limit: Int, values: IntArray?): Int {
                ++commonCalls
                val offset = text.getIndex()
                count[0] = if (offset % 2 == 0) 3 else 0
                lengths[0] = 1; lengths[1] = 2; lengths[2] = 3
                text.setIndex(offset + 4)
                return 4
            }
        }
        val j = DictionaryBreakEngine.PossibleWord()
        val k = CommonEngine.PossibleWord()
        val ji = StringCharacterIterator("abcdefghijklmnop")
        val ki = CommonStringIterator("abcdefghijklmnop")
        for (offset in listOf(0, 0, 1, 1, 2, 4, 2)) {
            ji.setIndex(offset); ki.setIndex(offset)
            val count = j.candidates(ji, jm, 16)
            assertEquals(count, k.candidates(ki, km, 16))
            assertEquals(ji.index, ki.getIndex())
            assertEquals(j.longestPrefix(), k.longestPrefix())
            assertEquals(javaCalls, commonCalls)
            if (count > 0) {
                assertEquals(j.backUp(ji), k.backUp(ki))
                j.markCurrent(); k.markCurrent()
                assertEquals(j.backUp(ji), k.backUp(ki))
                assertEquals(j.backUp(ji), k.backUp(ki))
                assertEquals(j.acceptMarked(ji), k.acceptMarked(ki))
                assertEquals(ji.index, ki.getIndex())
            }
        }
    }
}
