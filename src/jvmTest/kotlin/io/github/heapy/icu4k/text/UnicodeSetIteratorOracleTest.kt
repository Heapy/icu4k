package io.github.heapy.icu4k.text

import com.ibm.icu.text.UnicodeSet as JavaSet
import com.ibm.icu.text.UnicodeSetIterator as JavaIterator
import kotlin.random.Random
import kotlin.test.*

class UnicodeSetIteratorOracleTest {
    @Test fun mixedIterationRangesStringsSkippingAndResetMatch() {
        val random = Random(555)
        repeat(100) {
            val j = JavaSet(); val k = UnicodeSet()
            repeat(20) {
                val start = random.nextInt(0x10ff00)
                val end = start + random.nextInt(20)
                j.add(start, end); k.add(start, end)
            }
            j.add(0x10ffff); k.add(0x10ffff)
            for (s in listOf("", "ab", "abc", "xy", "😀x", "a", "😀")) { j.add(s); k.add(s) }
            val ji = JavaIterator(j); val ki = UnicodeSetIterator(k)
            repeat(800) {
                when (random.nextInt(10)) {
                    0 -> { ji.reset(); ki.reset() }
                    1 -> { assertSame(ji, ji.skipToStrings()); assertSame(ki, ki.skipToStrings()) }
                    2 -> { ji.reset(j); ki.reset(k) }
                    3, 4, 5 -> assertEquals(ji.nextRange(), ki.nextRange())
                    else -> assertEquals(ji.next(), ki.next())
                }
                assertEquals(ji.codepoint, ki.codepoint); assertEquals(ji.codepointEnd, ki.codepointEnd)
                assertEquals(ji.string, ki.string); assertEquals(ji.getString(), ki.getString())
            }
            val jStrings = j.iterator(); val kStrings = k.iterator()
            while (jStrings.hasNext()) { assertTrue(kStrings.hasNext()); assertEquals(jStrings.next(), kStrings.next()) }
            assertFalse(kStrings.hasNext())
            assertFailsWith<NoSuchElementException> { kStrings.next() }
            assertFailsWith<UnsupportedOperationException> { kStrings.remove() }
            assertEquals(j.strings().toList(), k.strings().toList())
        }
    }
    @Test fun stringViewsFollowOriginalStorageAcrossSetAndCompact() {
        val j = JavaSet(); val k = UnicodeSet()
        val je = j.strings(); val ke = k.strings()
        j.add("ab"); k.add("ab")
        assertEquals(je.toList(), ke.toList())
        var jv = j.strings(); var kv = k.strings()
        j.add("abc"); k.add("abc")
        assertEquals(jv.toList(), kv.toList())
        j.set(j); k.set(k)
        j.add("xy"); k.add("xy")
        assertEquals(jv.toList(), kv.toList())
        jv = j.strings(); kv = k.strings()
        j.clear(); k.clear(); j.compact(); k.compact()
        j.add("new"); k.add("new")
        assertEquals(jv.toList(), kv.toList())
    }
}
