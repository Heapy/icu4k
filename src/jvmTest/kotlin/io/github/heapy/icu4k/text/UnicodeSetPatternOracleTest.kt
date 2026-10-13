package io.github.heapy.icu4k.text

import com.ibm.icu.text.UnicodeSet as JavaSet
import kotlin.random.Random
import kotlin.test.*

class UnicodeSetPatternOracleTest {
    @Test fun generatedPatternsMatchAndRoundTripInIcu() {
        val random = Random(807)
        repeat(1200) { seed ->
            val j = JavaSet(); val k = UnicodeSet()
            repeat(15) {
                val start = if (random.nextBoolean()) random.nextInt(256) else random.nextInt(0x110000)
                val end = minOf(start + random.nextInt(4), 0x10ffff)
                j.add(start, end); k.add(start, end)
            }
            if (seed % 4 == 0) { j.complement(); k.complement() }
            if (seed % 3 == 0) for (s in listOf("", "a-b", "😀x", "[ : ]", charArrayOf(0xd800.toChar(), 'x', 0xdc00.toChar()).concatToString())) { j.add(s); k.add(s) }
            if (seed % 5 == 0) { j.add(0xdbff, 0xdc00); k.add(0xdbff, 0xdc00) }
            if (seed % 7 == 0) { j.add(0xd800, 0xd801); k.add(0xd800, 0xd801); j.add(0xdc02); k.add(0xdc02) }
            for (escape in listOf(false, true)) {
                val pattern = k.toPattern(escape)
                assertEquals(j.toPattern(escape), pattern, "$seed $escape")
                assertEquals(j, JavaSet(pattern))
                for (strings in listOf(false, true)) assertEquals(j._generatePattern(StringBuffer("prefix"), escape, strings).toString(), k._generatePattern(StringBuilder("prefix"), escape, strings).toString())
            }
        }
    }
}
