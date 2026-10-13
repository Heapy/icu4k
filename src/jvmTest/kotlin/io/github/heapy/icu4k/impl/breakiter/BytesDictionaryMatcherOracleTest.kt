package com.ibm.icu.impl.breakiter

import com.ibm.icu.util.BytesTrieBuilder
import com.ibm.icu.util.StringTrieBuilder
import io.github.heapy.icu4k.impl.breakiter.BytesDictionaryMatcher as CommonMatcher
import io.github.heapy.icu4k.text.StringCharacterIterator as CommonIterator
import java.text.StringCharacterIterator
import kotlin.test.*

class BytesDictionaryMatcherOracleTest {
    @Test fun offsetsJoinersLimitsSupplementaryAndFailedPrefixesMatch() {
        for (offset in listOf(0xe00, 0x10000)) {
            val builder = BytesTrieBuilder()
            for ((index, key) in listOf(byteArrayOf(1), byteArrayOf(1, 2), byteArrayOf(1, 2, 3), byteArrayOf(-2, -1), byteArrayOf(-1), byteArrayOf(7, 8, 9)).withIndex()) builder.add(key, key.size, index * 17 - 1)
            val buffer = builder.buildByteBuffer(StringTrieBuilder.Option.SMALL)
            val raw = ByteArray(buffer.remaining()).also { buffer.get(it) }
            val j = BytesDictionaryMatcher(raw, 0x1000000 or offset)
            val k = CommonMatcher(raw, 0x1000000 or offset)
            fun string(vararg cp: Int): String = StringBuilder().apply { cp.forEach { appendCodePoint(it) } }.toString()
            val texts = listOf("", string(offset + 1, offset + 2, offset + 3), string(offset + 1, offset + 2, offset + 4), string(0x200c, 0x200d), "x", string(offset + 7, offset + 8, offset + 4), charArrayOf(0xffff.toChar(), 'a').concatToString())
            for (text in texts) for (max in listOf(0, 1, 2, 3, 20)) for (limit in 0..4) for (withValues in listOf(false, true)) {
                val ji = StringCharacterIterator(text)
                val ki = CommonIterator(text)
                val jl = IntArray(4) { -77 }; val kl = jl.copyOf()
                val jc = intArrayOf(91); val kc = jc.copyOf()
                val jv = if (withValues) IntArray(4) { -88 } else null
                val kv = jv?.copyOf()
                assertEquals(j.matches(ji, max, jl, jc, limit, jv), k.matches(ki, max, kl, kc, limit, kv))
                assertContentEquals(jl, kl); assertContentEquals(jc, kc); assertContentEquals(jv, kv)
                assertEquals(ji.index, ki.getIndex())
                assertEquals(j.type, k.getType())
            }
        }
    }
}
