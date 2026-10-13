package com.ibm.icu.impl.breakiter

import com.ibm.icu.util.CharsTrie as JavaTrie
import com.ibm.icu.util.CharsTrieBuilder
import com.ibm.icu.util.StringTrieBuilder
import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.impl.breakiter.DictionaryData as CommonData
import io.github.heapy.icu4k.impl.breakiter.CharsDictionaryMatcher as CommonMatcher
import io.github.heapy.icu4k.text.StringCharacterIterator as CommonCursor
import java.text.StringCharacterIterator
import kotlin.test.*

class CharsDictionaryMatcherOracleTest {
    private fun check(raw: CharSequence, keys: List<String>) {
        val j = CharsDictionaryMatcher(raw); val k = CommonMatcher(raw)
        for (text in keys) for (limit in listOf(0, 1, 20)) for (max in listOf(0, 1, text.length, text.length + 2)) {
            val ji = StringCharacterIterator(text); val ki = CommonCursor(text)
            val jl = IntArray(20); val kl = IntArray(20)
            val jv = IntArray(20); val kv = IntArray(20)
            val jc = intArrayOf(91); val kc = intArrayOf(91)
            assertEquals(j.matches(ji, max, jl, jc, limit, jv), k.matches(ki, max, kl, kc, limit, kv))
            assertContentEquals(jl, kl); assertContentEquals(jv, kv); assertContentEquals(jc, kc)
            assertEquals(ji.index, ki.getIndex())
            assertEquals(j.type, k.getType())
        }
    }
    @Test fun cjkAndSupplementaryCandidatesMatchIcu() {
        val data = CommonData.read(ICUBinary.getRequiredData("brkitr/cjdict.dict")) as CommonData.TrieData.Chars
        val iterator = JavaTrie(data.data, 0).iterator()
        val keys = ArrayList<String>()
        while (iterator.hasNext() && keys.size < 1000) keys.add(iterator.next().chars.toString())
        check(data.data, keys)
        val words = listOf("a", "ab", "😀", "😀a", "😀ab", "\u200c\u200d")
        val builder = CharsTrieBuilder()
        words.forEachIndexed { index, key -> builder.add(key, index * 19 - 1) }
        check(builder.buildCharSequence(StringTrieBuilder.Option.SMALL), words + listOf("", "x", "😀x", charArrayOf(0xffff.toChar(), 'a').concatToString()))
    }
}
