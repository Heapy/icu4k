package io.github.heapy.icu4k.text

import com.ibm.icu.text.UnicodeSet as JavaSet
import kotlin.random.Random
import kotlin.test.*

class UnicodeSetRankOracleTest {
    @Test fun ranksAndSelectionsMatchIcu() {
        val random = Random(142)
        repeat(100) { seed ->
            val j = JavaSet(); val k = UnicodeSet()
            repeat(30) {
                val start = random.nextInt(0x110000)
                val end = minOf(0x10ffff, start + random.nextInt(1000))
                j.add(start, end); k.add(start, end)
            }
            if (seed % 2 == 0) { j.complement(); k.complement() }
            j.add(0x10ffff).add("abc").add(""); k.add(0x10ffff).add("abc").add("")
            if (seed % 3 == 0) { j.freeze(); k.freeze() }
            for (c in listOf(0, 0xd800, 0xffff, 0x10000, 0x10ffff) + List(300) { random.nextInt(0x110000) }) assertEquals(j.indexOf(c), k.indexOf(c))
            for (index in listOf(-1, Int.MIN_VALUE, Int.MAX_VALUE, 0, j.size(), j.size() - 1) + List(300) { random.nextInt(j.size() + 1) }) {
                assertEquals(j.charAt(index), k.charAt(index))
                val c = k.charAt(index)
                if (c >= 0) assertEquals(index, k.indexOf(c))
            }
        }
        assertFailsWith<IllegalArgumentException> { UnicodeSet().indexOf(-1) }
        assertFailsWith<IllegalArgumentException> { UnicodeSet().indexOf(0x110000) }
    }
}
