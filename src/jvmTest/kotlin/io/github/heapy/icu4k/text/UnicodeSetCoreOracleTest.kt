package io.github.heapy.icu4k.text

import com.ibm.icu.text.UnicodeSet as JavaSet
import kotlin.random.Random
import kotlin.test.*

class UnicodeSetCoreOracleTest {
    private fun compare(j: JavaSet, k: UnicodeSet, probes: List<Int>) {
        assertEquals(j.size(), k.size()); assertEquals(j.isEmpty, k.isEmpty()); assertEquals(j.hasStrings(), k.hasStrings())
        assertEquals(j.rangeCount, k.getRangeCount()); assertEquals(j.hashCode(), k.hashCode())
        for (index in 0 until j.rangeCount) {
            val start = j.getRangeStart(index); val end = j.getRangeEnd(index)
            assertEquals(start, k.getRangeStart(index)); assertEquals(end, k.getRangeEnd(index))
            assertEquals(j.contains(start, end), k.contains(start, end))
        }
        for (cp in probes) assertEquals(j.contains(cp), k.contains(cp), "U+${cp.toString(16)}")
        for (str in listOf("", "ab", "abc", "😀", "a", "xy")) assertEquals(j.contains(str), k.contains(str))
        assertEquals(k, UnicodeSet(k)); assertEquals(k.hashCode(), UnicodeSet(k).hashCode())
    }
    @Test fun mixedInsertionUnionAndCompactionMatchIcu() {
        val random = Random(166)
        repeat(50) {
            val j = JavaSet(); val k = UnicodeSet()
            repeat(200) {
                val a = when (random.nextInt(5)) { 0 -> 0; 1 -> 0x10ffff; 2 -> random.nextInt(256); else -> random.nextInt(0x110000) }
                val b = random.nextInt(0x110000)
                when (random.nextInt(8)) {
                    0 -> { j.add(a); k.add(a) }
                    1 -> { val s = listOf("", "ab", "abc", "😀", "xy")[random.nextInt(5)]; j.add(s); k.add(s) }
                    2 -> { j.compact(); k.compact() }
                    3 -> { j.addAll(j); k.addAll(k) }
                    4 -> {
                        val jj = JavaSet(minOf(a, b), maxOf(a, b)).add("ab")
                        val kk = UnicodeSet(minOf(a, b), maxOf(a, b)).add("ab")
                        j.addAll(jj); k.addAll(kk)
                    }
                    5 -> { j.clear(); k.clear() }
                    else -> { j.add(a, b); k.add(a, b) }
                }
                compare(j, k, listOf(a, b, 0, 0xffff, 0x10000, 0x10ffff))
            }
        }
    }
    @Test fun singletonMergingAllUnicodeAndPairConstructors() {
        val j = JavaSet(); val k = UnicodeSet()
        for (cp in listOf(0x10ffff, 1, 3, 2, 0, 0xffff, 0x10000)) { j.add(cp); k.add(cp); compare(j, k, listOf(cp)) }
        j.add(0, 0x10ffff); k.add(0, 0x10ffff)
        for (cp in 0..0x10ffff) assertEquals(j.contains(cp), k.contains(cp))
        val pairs = intArrayOf(0, 1, 3, 5, 0xffff, 0x10000)
        compare(JavaSet(*pairs), UnicodeSet(*pairs), listOf(0, 2, 3, 6, 0x10000))
        for (cp in listOf(-1, 0x110000)) {
            assertFailsWith<IllegalArgumentException> { k.add(cp) }
            assertFailsWith<IllegalArgumentException> { k.contains(cp) }
        }
    }
}
