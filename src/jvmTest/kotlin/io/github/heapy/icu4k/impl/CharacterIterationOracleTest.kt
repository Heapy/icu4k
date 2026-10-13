package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.CharacterIteration as JavaIteration
import com.ibm.icu.text.StringCharacterIterator as JavaIterator
import io.github.heapy.icu4k.text.StringCharacterIterator
import kotlin.random.Random
import kotlin.test.*

@Suppress("DEPRECATION")
class CharacterIterationOracleTest {
    @Test fun randomBoundedTraversalMatchesIcuIncludingNoncharacters() {
        val random = Random(298)
        repeat(200) {
            val text = CharArray(30) {
                when (random.nextInt(4)) {
                    0 -> 0xffff.toChar()
                    1 -> random.nextInt(0xd800, 0xe000).toChar()
                    else -> random.nextInt(0x10000).toChar()
                }
            }.concatToString()
            val begin = random.nextInt(text.length + 1)
            val end = random.nextInt(begin, text.length + 1)
            val k = StringCharacterIterator(text, begin, end, begin)
            val j = JavaIterator(text, begin, end, begin)
            repeat(300) {
                when (random.nextInt(12)) {
                    0 -> assertEquals(j.first(), k.first())
                    1 -> assertEquals(j.last(), k.last())
                    2 -> assertEquals(j.next(), k.next())
                    3 -> assertEquals(j.previous(), k.previous())
                    4 -> {
                        val n = random.nextInt(begin, end + 1)
                        assertEquals(j.setIndex(n), k.setIndex(n))
                    }
                    5 -> assertEquals(JavaIteration.next32(j), CharacterIteration.next32(k))
                    6 -> assertEquals(JavaIteration.previous32(j), CharacterIteration.previous32(k))
                    7 -> assertEquals(JavaIteration.current32(j), CharacterIteration.current32(k))
                    8 -> {
                        val lead = j.current().code
                        assertEquals(JavaIteration.nextTrail32(j, lead), CharacterIteration.nextTrail32(k, lead))
                    }
                    9 -> {
                        val copy = k.clone()
                        assertEquals(k, copy)
                        copy.next()
                        assertEquals(j.index, k.getIndex())
                    }
                    else -> assertEquals(j.current(), k.current())
                }
                assertEquals(j.index, k.getIndex())
                assertEquals(j.current(), k.current())
                assertEquals(j.hashCode(), k.hashCode())
            }
            k.setText("replacement"); j.setText("replacement")
            assertEquals(j.index, k.getIndex())
            assertEquals(j.beginIndex, k.getBeginIndex())
            assertEquals(j.endIndex, k.getEndIndex())
            assertEquals(j.hashCode(), k.hashCode())
        }
    }
}
