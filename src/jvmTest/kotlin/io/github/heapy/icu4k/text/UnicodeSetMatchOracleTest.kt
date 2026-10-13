package io.github.heapy.icu4k.text

import com.ibm.icu.text.UnicodeSet as JavaSet
import com.ibm.icu.text.ReplaceableString as JavaText
import kotlin.random.Random
import kotlin.test.*

class UnicodeSetMatchOracleTest {
    @Test fun matchesInBothDirectionsAndIncrementally() {
        val random = Random(731)
        repeat(150) { seed ->
            val j = JavaSet().add(65, 67).add(0x1f600)
            val k = UnicodeSet().add(65, 67).add(0x1f600)
            repeat(12) { val start = random.nextInt(0x110000); val end = minOf(start + random.nextInt(600), 0x10ffff); j.add(start, end); k.add(start, end) }
            if (seed % 2 == 0) { j.add(0xffff); k.add(0xffff) }
            for (s in listOf("", "ab", "abc", "cd", "AB", "ABC", "😀x", "x😀")) { j.add(s); k.add(s) }
            if (seed % 3 == 0) { j.freeze(); k.freeze() }
            for (v in 0..255) assertEquals(j.matchesIndexValue(v), k.matchesIndexValue(v))
            val text = if (seed % 2 == 0) "ABabc😀x!cd" else CharArray(12) { random.nextInt(0x10000).toChar() }.concatToString()
            val jt = JavaText(text); val kt = ReplaceableString(text)
            for (start in text.indices) for (limit in -1..text.length) for (incremental in listOf(false, true)) {
                val jo = intArrayOf(start); val ko = intArrayOf(start)
                assertEquals(j.matches(jt, jo, limit, incremental), k.matches(kt, ko, limit, incremental), "$seed $start $limit $incremental")
                assertEquals(jo[0], ko[0])
            }
        }
    }
}
