package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.USerializedSet as JavaSet
import kotlin.random.Random
import kotlin.test.*

class USerializedSetOracleTest {
    @Test fun randomizedBmpAndSupplementaryBoundariesMatch() {
        val random = Random(275)
        repeat(200) {
            val boundaries = (List(random.nextInt(1, 100)) { random.nextInt(0x110000) } + listOf(0, 0xffff, 0x10000, 0x10ffff)).distinct().sorted()
            val bmp = boundaries.takeWhile { it <= 0xffff }
            val supp = boundaries.drop(bmp.size)
            val length = bmp.size + supp.size * 2
            val serialized = buildList<Char> {
                add(0.toChar()); add(0.toChar()) // Exercise a non-zero input offset.
                add((length or 0x8000).toChar()); add(bmp.size.toChar())
                bmp.forEach { add(it.toChar()) }
                supp.forEach { add((it shr 16).toChar()); add(it.toChar()) }
            }.toCharArray()
            val j = JavaSet(); val k = USerializedSet()
            assertEquals(j.getSet(serialized, 2), k.getSet(serialized, 2))
            assertEquals(j.countRanges(), k.countRanges())
            for (index in -1..j.countRanges() + 2) {
                val a = intArrayOf(-1, -1); val b = a.copyOf()
                assertEquals(j.getRange(index, a), k.getRange(index, b)); assertContentEquals(a, b)
            }
            val probes = boundaries.flatMap { listOf(it - 1, it, it + 1) } + List(500) { random.nextInt(-2, 0x110002) }
            for (cp in probes) assertEquals(j.contains(cp), k.contains(cp), "U+${cp.toString(16)}")
            serialized.fill(0.toChar())
            for (cp in probes) assertEquals(j.contains(cp), k.contains(cp))
        }
    }
    @Test fun singletonAndFailureStatesMatch() {
        for (cp in listOf(-1, 0, 1, 0xfffe, 0xffff, 0x10000, 0x10001, 0x10fffe, 0x10ffff, 0x110000)) {
            val j = JavaSet(); val k = USerializedSet()
            j.setToOne(cp); k.setToOne(cp)
            assertEquals(j.countRanges(), k.countRanges())
            val a = IntArray(2); val b = IntArray(2)
            assertEquals(j.getRange(0, a), k.getRange(0, b)); assertContentEquals(a, b)
            for (probe in listOf(-1, 0, cp - 1, cp, cp + 1, 0xffff, 0x10000, 0x10ffff)) assertEquals(j.contains(probe), k.contains(probe))
        }
        for (raw in listOf(charArrayOf(2.toChar(), 0.toChar()), charArrayOf(0x8004.toChar(), 0.toChar()))) {
            val j = JavaSet(); val k = USerializedSet()
            assertFailsWith<IndexOutOfBoundsException> { j.getSet(raw, 0) }
            assertFailsWith<IndexOutOfBoundsException> { k.getSet(raw, 0) }
            assertEquals(j.countRanges(), k.countRanges())
            assertEquals(j.getRange(0, IntArray(2)), k.getRange(0, IntArray(2)))
        }
    }
}
