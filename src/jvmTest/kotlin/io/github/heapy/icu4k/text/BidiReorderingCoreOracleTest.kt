// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import com.ibm.icu.text.Bidi as Source
import kotlin.random.Random
import kotlin.test.*

class BidiReorderingCoreOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Exception) {
        when (e) { is IndexOutOfBoundsException -> "bounds"; is NullPointerException -> "null"; else -> e.javaClass.simpleName to e.message }
    }
    @Test fun logicalVisualPermutationsAndInvalidEmbeddingLevels() {
        val random = Random(78_306)
        val cases = listOf<ByteArray?>(null, byteArrayOf(), byteArrayOf(0), byteArrayOf(1), byteArrayOf(125, 126, 125), byteArrayOf(127), byteArrayOf(-128),
            byteArrayOf(0, 1, 2, 2, 1, 0), ByteArray(100) { 126 }, ByteArray(50) { 125 })
        fun compare(levels: ByteArray?) {
            val saved = levels?.copyOf()
            assertContentEquals(Source.reorderLogical(levels), BidiReorderingCore.reorderLogical(levels), "logical ${levels?.toList()}")
            assertContentEquals(Source.reorderVisual(levels), BidiReorderingCore.reorderVisual(levels), "visual ${levels?.toList()}")
            assertContentEquals(saved, levels)
        }
        cases.forEach(::compare)
        repeat(20_000) { i ->
            compare(ByteArray(random.nextInt(0, 200)) { if (i % 5 == 0) random.nextInt(-128, 128).toByte() else random.nextInt(0, 127).toByte() })
        }
    }
    @Test fun invertedMapsHolesDuplicatesAndNoMappingSentinels() {
        val random = Random(78_307)
        for (map in listOf(intArrayOf(), intArrayOf(-1, -1), intArrayOf(2, 2, 2), intArrayOf(3, -1, 1), intArrayOf(0, 0, 0), intArrayOf(Int.MIN_VALUE, -8, 0)))
            assertContentEquals(Source.invertMap(map), BidiReorderingCore.invertMap(map), map.toList().toString())
        repeat(10_000) {
            val map = IntArray(random.nextInt(0, 100)) { random.nextInt(-5, 120) }
            assertContentEquals(Source.invertMap(map), BidiReorderingCore.invertMap(map))
        }
        assertEquals(outcome { Source.invertMap(null) }, outcome { BidiReorderingCore.invertMap(null) })
        assertEquals(outcome { Source.invertMap(intArrayOf(Int.MAX_VALUE)) }, outcome { BidiReorderingCore.invertMap(intArrayOf(Int.MAX_VALUE)) })
    }
    @Test fun objectReorderingSlicesValidationAndFailureSideEffects() {
        val levelsList = listOf<ByteArray?>(null, byteArrayOf(), byteArrayOf(0, 1, 2, 2, 1, 0), byteArrayOf(127, 0, 1), byteArrayOf(-1, 0, 1))
        for (levels in levelsList) for (levelStart in listOf(-1, 0, 1, 5)) for (objectStart in listOf(-1, 0, 1, 5))
            for (count in listOf(-1, 0, 1, 3, 6)) for (isNull in listOf(false, true)) {
                val s = if (isNull) null else arrayOf<Any?>("a", "b", 3, null, "e", "f")
                val p = s?.copyOf()
                assertEquals(outcome { Source.reorderVisually(levels, levelStart, s, objectStart, count) },
                    outcome { BidiReorderingCore.reorderVisually(levels, levelStart, p, objectStart, count) }, "${levels?.toList()} $levelStart $objectStart $count $isNull")
                assertContentEquals(s, p)
            }
    }
    @Test fun sourceUnicodeBaseDirectionsAndCodeUnitRequiresBidi() {
        assertEquals(Source.getBaseDirection(null), BidiReorderingCore.baseDirection(null))
        for (c in 0..0x10ffff) {
            val text = String(Character.toChars(c))
            assertEquals(Source.getBaseDirection(text), BidiReorderingCore.baseDirection(text), "base ${c.toString(16)}")
            val chars = text.toCharArray()
            assertEquals(Source.requiresBidi(chars, 0, chars.size), BidiReorderingCore.requiresBidi(chars, 0, chars.size), "requires ${c.toString(16)}")
        }
        for (text in listOf("", "123", "1 אבA", "12 Aאב", "\u2067אב\u2069A", "😀אב", "𝄞a", "\ud800א", "\udc00א"))
            assertEquals(Source.getBaseDirection(StringBuilder(text)), BidiReorderingCore.baseDirection(StringBuilder(text)))
        for (text in listOf<CharArray?>(null, charArrayOf(), "אx".toCharArray(), "A".toCharArray()))
            for (start in listOf(-1, 0, 1, 5)) for (limit in listOf(-1, 0, 1, 5))
                assertEquals(outcome { Source.requiresBidi(text, start, limit) }, outcome { BidiReorderingCore.requiresBidi(text, start, limit) })
    }
    @Test fun customCharSequenceReadOrderMatchesSource() {
        fun traced(value: String, trace: MutableList<String>): CharSequence = object : CharSequence {
            override val length: Int get() { trace.add("length"); return value.length }
            override fun get(index: Int): Char { trace.add("char:$index"); return value[index] }
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = error("unexpected subsequence")
        }
        for (text in listOf("", "123", "(1)😀אבA", "𝄞אב", "\udc00אב", "\ud800xאב", "\u2067אב")) {
            val st = mutableListOf<String>(); val pt = mutableListOf<String>()
            assertEquals(Source.getBaseDirection(traced(text, st)), BidiReorderingCore.baseDirection(traced(text, pt)))
            assertEquals(st, pt, text)
        }
    }

}
