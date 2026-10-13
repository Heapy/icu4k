package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.BMPSet as JavaBMPSet
import com.ibm.icu.text.UnicodeSet as JavaSet
import com.ibm.icu.util.OutputInt as JavaOutput
import io.github.heapy.icu4k.text.UnicodeSet.SpanCondition
import io.github.heapy.icu4k.util.OutputInt
import kotlin.random.Random
import kotlin.test.*

@Suppress("DEPRECATION")
class BMPSetOracleTest {
    @Test fun tablesFullUnicodeAndBothSpanDirectionsMatchIcu() {
        val random = Random(912)
        repeat(25) { seed ->
            val set = JavaSet()
            repeat(200) {
                val start = random.nextInt(0x110000)
                set.add(start, minOf(start + random.nextInt(150), 0x10ffff))
            }
            set.add(65, 90).add(0x7c0, 0x840).add(0xd7ff, 0xe001).add(0xffff, 0x10001)
            if (seed % 3 == 0) set.complement()
            if (seed == 0) set.clear()
            if (seed == 1) set.add(0, 0x10ffff)
            val boundaries = ArrayList<Int>()
            for (i in 0 until set.rangeCount) { boundaries.add(set.getRangeStart(i)); boundaries.add(set.getRangeEnd(i) + 1) }
            if (boundaries.lastOrNull() != 0x110000) boundaries.add(0x110000)
            val list = boundaries.toIntArray()
            val j = JavaBMPSet(list, list.size); val k = BMPSet(list, list.size)
            val copy = BMPSet(k, list.copyOf(), list.size)
            for (cp in 0..0x110001) {
                assertEquals(j.contains(cp), k.contains(cp))
                assertEquals(j.contains(cp), copy.contains(cp))
            }
            repeat(10) {
                val text = CharArray(35) {
                    when (random.nextInt(6)) {
                        0 -> random.nextInt(256).toChar()
                        1 -> random.nextInt(0xd800, 0xe100).toChar()
                        2 -> 0xffff.toChar()
                        else -> random.nextInt(0x10000).toChar()
                    }
                }.concatToString()
                for (condition in SpanCondition.entries) {
                    val jc = JavaSet.SpanCondition.valueOf(condition.name)
                    for (start in 0..text.length) {
                        val jo = JavaOutput(-1); val ko = OutputInt(-1)
                        assertEquals(j.span(text, start, jc, jo), k.span(text, start, condition, ko), "$seed $condition $start")
                        assertEquals(jo.value, ko.value)
                        assertEquals(j.span(text, start, jc, null), k.span(text, start, condition, null))
                    }
                    for (limit in 1..text.length) assertEquals(j.spanBack(text, limit, jc), k.spanBack(text, limit, condition), "$seed $condition $limit")
                }
            }
        }
    }
}
