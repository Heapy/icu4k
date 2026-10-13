package io.github.heapy.icu4k.text

import com.ibm.icu.text.UnicodeSet as JavaSet
import kotlin.random.Random
import kotlin.test.*

class UnicodeSetAlgebraOracleTest {
    private fun compare(j: JavaSet, k: UnicodeSet) {
        assertEquals(j.size(), k.size()); assertEquals(j.rangeCount, k.getRangeCount()); assertEquals(j.hashCode(), k.hashCode())
        assertEquals(j.hasStrings(), k.hasStrings()); assertEquals(j.isEmpty, k.isEmpty())
        for (i in 0 until j.rangeCount) { assertEquals(j.getRangeStart(i), k.getRangeStart(i)); assertEquals(j.getRangeEnd(i), k.getRangeEnd(i)) }
        for (str in listOf("", "ab", "abc", "xy", "😀", "a")) assertEquals(j.contains(str), k.contains(str), str)
    }
    @Test fun randomizedRangeStringAndSetAlgebraMatches() {
        val random = Random(641)
        repeat(100) {
            val j = JavaSet(); val k = UnicodeSet()
            repeat(300) {
                val a = random.nextInt(0x110000); val b = random.nextInt(0x110000)
                val str = listOf("", "ab", "abc", "xy", "😀", "a")[random.nextInt(6)]
                when (random.nextInt(15)) {
                    0 -> { j.add(a, b); k.add(a, b) }
                    1 -> { j.remove(a, b); k.remove(a, b) }
                    2 -> { j.retain(a, b); k.retain(a, b) }
                    3 -> { j.complement(a, b); k.complement(a, b) }
                    4 -> { j.complement(); k.complement() }
                    5 -> { j.add(str); k.add(str) }
                    6 -> { j.remove(str); k.remove(str) }
                    7 -> { j.retain(str); k.retain(str) }
                    8 -> { j.complement(str); k.complement(str) }
                    9 -> { j.removeAllStrings(); k.removeAllStrings() }
                    10 -> { j.retainAll(j); k.retainAll(k) }
                    11 -> { j.complementAll(j); k.complementAll(k) }
                    12 -> { j.removeAll(j); k.removeAll(k) }
                    else -> {
                        val jo = JavaSet(minOf(a, b), maxOf(a, b)).add(str)
                        val ko = UnicodeSet(minOf(a, b), maxOf(a, b)).add(str)
                        when (random.nextInt(4)) {
                            0 -> { j.addAll(jo); k.addAll(ko) }
                            1 -> { j.retainAll(jo); k.retainAll(ko) }
                            2 -> { j.removeAll(jo); k.removeAll(ko) }
                            else -> { j.complementAll(jo); k.complementAll(ko) }
                        }
                    }
                }
                compare(j, k)
            }
        }
    }
    @Test fun boundaryCrossingsAndAdjacentRangesMatch() {
        val points = listOf(0, 1, 2, 0xfffe, 0xffff, 0x10000, 0x10fffe, 0x10ffff)
        for (a in points) for (b in points) {
            val j = JavaSet().add(0, 2).add(0xfffe, 0x10000).add(0x10fffe, 0x10ffff).add("ab")
            val k = UnicodeSet().add(0, 2).add(0xfffe, 0x10000).add(0x10fffe, 0x10ffff).add("ab")
            j.remove(a, b); k.remove(a, b); compare(j, k)
            j.complement(a, b); k.complement(a, b); compare(j, k)
            j.retain(a, b); k.retain(a, b); compare(j, k)
        }
    }
}
