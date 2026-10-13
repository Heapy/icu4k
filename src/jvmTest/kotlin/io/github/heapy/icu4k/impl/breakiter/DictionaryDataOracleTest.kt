package com.ibm.icu.impl.breakiter

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.breakiter.DictionaryData as CommonData
import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.BytesTrie
import io.github.heapy.icu4k.text.StringCharacterIterator as CommonCursor
import java.nio.ByteBuffer
import java.text.StringCharacterIterator
import kotlin.test.*

class DictionaryDataOracleTest {
    @Test fun pinnedDictionariesDecodeAndByteWordMatchesAgree() {
        for (name in listOf("thaidict", "laodict", "khmerdict", "burmesedict", "cjdict")) {
            val path = "brkitr/$name.dict"
            val raw = ICUData.getBytes(path)!!
            val upstream = com.ibm.icu.impl.ICUData.getRequiredStream("/" + com.ibm.icu.impl.ICUData.ICU_BASE_NAME + "/" + path).use { it.readBytes() }
            assertContentEquals(upstream, raw)
            val kbytes = BinaryBuffer(raw)
            val common = assertNotNull(CommonData.read(kbytes))
            val jbytes = ByteBuffer.wrap(upstream)
            com.ibm.icu.impl.ICUBinary.readHeader(jbytes, 0x44696374, null)
            val indexes = IntArray(8) { jbytes.int }
            assertContentEquals(indexes, common.indexes)
            jbytes.position(jbytes.position() + indexes[0] - 32)
            val size = indexes[3] - indexes[0]
            when (common) {
                is CommonData.TrieData.Chars -> assertEquals(com.ibm.icu.impl.ICUBinary.getString(jbytes, size / 2, size and 1), common.data)
                is CommonData.TrieData.Bytes -> {
                    val expected = ByteArray(size).also { jbytes.get(it) }
                    assertContentEquals(expected, common.data)
                    val jm = BytesDictionaryMatcher(expected, indexes[5])
                    val km = common.matcher()
                    val entries = BytesTrie(common.data, 0).iterator()
                    var sampled = 0
                    while (entries.hasNext() && sampled++ < 500) {
                        val entry = entries.next()
                        val text = buildString {
                            for (i in 0 until entry.bytesLength()) {
                                val unit = entry.byteAt(i).toInt() and 255
                                append(when (unit) { 255 -> 0x200d; 254 -> 0x200c; else -> (indexes[5] and 0x1fffff) + unit }.toChar())
                            }
                        }
                        for (limit in listOf(0, 1, 20)) for (max in listOf(0, 1, text.length, text.length + 2)) {
                            val j = StringCharacterIterator(text); val k = CommonCursor(text)
                            val jl = IntArray(20); val kl = IntArray(20)
                            val jv = IntArray(20); val kv = IntArray(20)
                            val jc = intArrayOf(91); val kc = intArrayOf(91)
                            assertEquals(jm.matches(j, max, jl, jc, limit, jv), km.matches(k, max, kl, kc, limit, kv))
                            assertContentEquals(jl, kl); assertContentEquals(jv, kv); assertContentEquals(jc, kc)
                            assertEquals(j.index, k.getIndex())
                        }
                    }
                    assertTrue(sampled > 1)
                }
            }
            assertEquals(jbytes.position(), kbytes.position)
        }
    }
}
