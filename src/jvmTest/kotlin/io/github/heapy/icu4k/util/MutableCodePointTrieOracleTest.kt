package io.github.heapy.icu4k.util

import com.ibm.icu.util.MutableCodePointTrie as JavaTrie
import com.ibm.icu.util.CodePointMap as JavaMap
import kotlin.random.Random
import kotlin.test.*

class MutableCodePointTrieOracleTest {
    @Test fun randomMutationsRangesAndCopiesMatchOriginal() {
        val random = Random(318)
        val j = JavaTrie(17, -3)
        val k = MutableCodePointTrie(17, -3)
        val jr = JavaMap.Range(); val kr = CodePointMap.Range()
        repeat(3000) { n ->
            val start = random.nextInt(0x110000)
            val value = random.nextInt()
            if (n % 3 == 0) { j.set(start, value); k.set(start, value) }
            else {
                val end = minOf(0x10ffff, start + random.nextInt(4096))
                j.setRange(start, end, value); k.setRange(start, end, value)
            }
            repeat(4) {
                val c = random.nextInt(0x110000)
                assertEquals(j[c], k[c], "$n $c")
                val mask = if (n % 2 == 0) -1 else 3
                assertEquals(j.getRange(c, JavaMap.ValueFilter { it and mask }, jr), k.getRange(c, CodePointMap.ValueFilter { it and mask }, kr))
                assertEquals(jr.getEnd(), kr.getEnd(), "$n range $c")
                assertEquals(jr.getValue(), kr.getValue())
            }
        }
        for (c in 0..0x10ffff) if (j[c] != k[c]) fail("c=$c")
        val jc = j.clone(); val kc = k.clone()
        val jm = JavaTrie.fromCodePointMap(j); val km = MutableCodePointTrie.fromCodePointMap(k)
        j.setRange(0, 0x10ffff, 42); k.setRange(0, 0x10ffff, 42)
        for (c in 0..0x10ffff) {
            if (jc[c] != kc[c] || jm[c] != km[c] || kc[c] != km[c]) fail("copy c=$c")
        }
        var start = 0
        while (jc.getRange(start, null, jr)) {
            assertTrue(kc.getRange(start, null, kr))
            assertEquals(jr.getEnd(), kr.getEnd())
            assertEquals(jr.getValue(), kr.getValue())
            start = jr.getEnd() + 1
        }
        for (c in listOf(Int.MIN_VALUE, -1, 0x110000, Int.MAX_VALUE)) {
            assertEquals(j[c], k[c])
            assertFalse(k.getRange(c, null, kr))
            assertFailsWith<IllegalArgumentException> { k.set(c, 1) }
        }
    }
}
