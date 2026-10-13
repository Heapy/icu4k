package io.github.heapy.icu4k.text

import com.ibm.icu.text.UnicodeSet as JavaSet
import kotlin.random.Random
import kotlin.test.*

class UnicodeSetRelationsOracleTest {
    @Test fun setRangeAndStringRelationsMatchIcu() {
        val random = Random(615)
        repeat(1200) { seed ->
            fun pair(): Pair<JavaSet, UnicodeSet> {
                val j = JavaSet(); val k = UnicodeSet()
                repeat(10) { val a = random.nextInt(150); val b = a + random.nextInt(10); j.add(a, b); k.add(a, b) }
                for (s in listOf("", "ab", "abc", "cd", "😀x", "x😀")) if (random.nextBoolean()) { j.add(s); k.add(s) }
                if (random.nextBoolean()) { j.complement(); k.complement() }
                if (random.nextBoolean()) { j.freeze(); k.freeze() }
                return j to k
            }
            val (ja, ka) = pair(); val (jb, kb) = pair()
            assertEquals(ja.containsAll(jb), ka.containsAll(kb))
            assertEquals(ja.containsNone(jb), ka.containsNone(kb))
            assertEquals(ja.containsSome(jb), ka.containsSome(kb))
            assertTrue(ka.containsAll(ka))
            repeat(20) {
                val a = random.nextInt(0x110000); val b = random.nextInt(0x110000)
                assertEquals(ja.containsNone(a, b), ka.containsNone(a, b))
                assertEquals(ja.containsSome(a, b), ka.containsSome(a, b))
            }
            for (text in listOf("", "abcd", "abcab", "😀x😀", "x😀x", CharArray(12) { random.nextInt(0x10000).toChar() }.concatToString())) {
                assertEquals(ja.containsAll(text), ka.containsAll(text), "$seed all")
                assertEquals(ja.containsNone(text), ka.containsNone(text), "$seed none")
                assertEquals(ja.containsSome(text), ka.containsSome(text), "$seed some")
            }
        }
    }
}
