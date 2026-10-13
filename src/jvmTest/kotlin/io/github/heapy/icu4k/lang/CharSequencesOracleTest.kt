package io.github.heapy.icu4k.lang

import com.ibm.icu.lang.CharSequences as JavaSequences
import kotlin.random.Random
import kotlin.test.*

class CharSequencesOracleTest {
    @Test fun allStaticOperationsMatchOriginalUtf16Semantics() {
        val random = Random(713)
        repeat(3000) {
            fun text() = CharArray(random.nextInt(15)) { if (random.nextBoolean()) random.nextInt(0xd800, 0xe000).toChar() else random.nextInt(0x10000).toChar() }.concatToString()
            val a = text(); val b = if (random.nextBoolean()) a + text() else text()
            assertContentEquals(JavaSequences.codePoints(a), CharSequences.codePoints(a))
            assertEquals(JavaSequences.getSingleCodePoint(a), CharSequences.getSingleCodePoint(a))
            assertEquals(JavaSequences.compare(a, b), CharSequences.compare(a, b))
            assertEquals(JavaSequences.equalsChars(a, b), CharSequences.equalsChars(a, b))
            for (i in 0..a.length) {
                assertEquals(JavaSequences.onCharacterBoundary(a, i), CharSequences.onCharacterBoundary(a, i))
                for (j in 0..b.length) assertEquals(JavaSequences.matchAfter(a, b, i, j), CharSequences.matchAfter(a, b, i, j))
            }
            for (cp in listOf(0xd800, 0xdc00, 0x10000, 0x1f600, random.nextInt(0x110000))) {
                assertEquals(JavaSequences.indexOf(a, cp), CharSequences.indexOf(a, cp))
                assertEquals(JavaSequences.equals(cp, a), CharSequences.equals(cp, a))
                assertEquals(JavaSequences.compare(a, cp), CharSequences.compare(a, cp))
                assertEquals(JavaSequences.compare(cp, a), CharSequences.compare(cp, a))
            }
        }
    }
}
