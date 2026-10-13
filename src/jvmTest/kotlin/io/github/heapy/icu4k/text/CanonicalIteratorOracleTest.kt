package io.github.heapy.icu4k.text

import com.ibm.icu.text.CanonicalIterator as JavaIterator
import kotlin.random.Random
import kotlin.test.*

class CanonicalIteratorOracleTest {
    @Test fun canonicalResultSetsMatchOriginal() {
        val random = Random(911)
        val pieces = listOf("A", "Å", "é", "\u0301", "\u0323", "\u0327", "각", "\u1100", "\u1161", "\u11a8", "𝅘𝅥", "😀", CharArray(1) { 0xd800.toChar() }.concatToString())
        val inputs = listOf("", "Åd\u0307\u0327", "a\u0301\u0323", "\u0344", "\u1f82") + List(150) { buildString { repeat(random.nextInt(1, 4)) { append(pieces.random(random)) } } }
        val j = JavaIterator(""); val k = CanonicalIterator("")
        for (input in inputs) {
            j.setSource(input); k.setSource(input)
            assertEquals(j.getSource(), k.getSource())
            val expected = buildSet { while (true) add(j.next() ?: break) }
            val actual = buildSet { while (true) add(k.next() ?: break) }
            assertEquals(expected, actual, input)
            assertNull(k.next())
            k.reset()
            assertEquals(expected, buildSet { while (true) add(k.next() ?: break) })
        }
    }
    @Test fun permutationSetsAndGuardMatchOriginal() {
        for (input in listOf("", "A", "ab", "a\u0301\u0323", "😀\u0301", "\u0301a\u0323")) for (skip in listOf(false, true)) {
            val j = mutableSetOf("existing"); val k = mutableSetOf("existing")
            JavaIterator.permute(input, skip, j); CanonicalIterator.permute(input, skip, k)
            assertEquals(j, k, "$input $skip")
        }
        assertFailsWith<UnsupportedOperationException> { JavaIterator.permute("a".repeat(10), false, mutableSetOf()) }
        assertFailsWith<UnsupportedOperationException> { CanonicalIterator.permute("a".repeat(10), false, mutableSetOf()) }
    }
}
