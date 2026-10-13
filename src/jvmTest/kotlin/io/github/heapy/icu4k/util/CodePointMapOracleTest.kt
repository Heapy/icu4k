package io.github.heapy.icu4k.util

import com.ibm.icu.util.CodePointMap as JavaMap
import kotlin.random.Random
import kotlin.test.*

class CodePointMapOracleTest {
    @Test fun surrogateRangeOverridesMatchIcu() {
        val random = Random(78_005)
        val starts = intArrayOf(0, 0xd7fe, 0xd800, 0xdbff, 0xdc00, 0xe000, 0xe001, 0x10000, 0x110000)
        repeat(100) {
            val values = IntArray(starts.size - 1) { random.nextInt(4) }
            fun value(c: Int): Int = if (c !in 0..0x10ffff) -1 else values[starts.indexOfLast { it <= c }]
            fun range(start: Int, filter: ((Int) -> Int)?): Triple<Int, Int, Int>? {
                if (start !in 0..0x10ffff) return null
                var band = starts.indexOfLast { it <= start }
                val v = filter?.invoke(values[band]) ?: values[band]
                while (band + 1 < values.size && (filter?.invoke(values[band + 1]) ?: values[band + 1]) == v) band++
                return Triple(start, starts[band + 1] - 1, v)
            }
            val actual = object : CodePointMap() {
                override fun get(c: Int): Int = value(c)
                override fun getRange(start: Int, filter: ValueFilter?, result: Range): Boolean {
                    val r = range(start, filter?.let { f -> { v -> f.apply(v) } }) ?: return false
                    result.set(r.first, r.second, r.third)
                    return true
                }
            }
            val expected = object : JavaMap() {
                override fun get(c: Int): Int = value(c)
                override fun getRange(start: Int, filter: ValueFilter?, result: Range): Boolean {
                    val r = range(start, filter?.let { f -> { v -> f.apply(v) } }) ?: return false
                    result.set(r.first, r.second, r.third)
                    return true
                }
            }
            val filters: List<((Int) -> Int)?> = listOf(null, { it and 1 }, { 0 }, { it + 4 })
            for (start in starts + intArrayOf(-1, 0xd7ff, 0xdbfe, 0xdfff, 0x10ffff)) {
                for (option in CodePointMap.RangeOption.entries) for (surrogateValue in 0..5) for (filter in filters) {
                    val a = CodePointMap.Range()
                    val b = JavaMap.Range()
                    val fa = filter?.let { f -> CodePointMap.ValueFilter { f(it) } }
                    val fb = filter?.let { f -> JavaMap.ValueFilter { f(it) } }
                    assertEquals(expected.getRange(start, JavaMap.RangeOption.valueOf(option.name), surrogateValue, fb, b), actual.getRange(start, option, surrogateValue, fa, a))
                    assertEquals(b.start, a.getStart())
                    assertEquals(b.end, a.getEnd())
                    assertEquals(b.value, a.getValue())
                }
            }
            val text = charArrayOf('a', 0xd800.toChar(), 0xdc00.toChar(), 0xd800.toChar(), 'b', 0xdc00.toChar()).concatToString()
            for (start in 0..text.length) {
                val a = actual.stringIterator(text, start)
                val b = expected.stringIterator(text, start)
                repeat(text.length + 1) {
                    assertEquals(b.next(), a.next())
                    assertEquals(b.index, a.getIndex())
                    assertEquals(b.codePoint, a.getCodePoint())
                    assertEquals(b.value, a.getValue())
                }
                repeat(text.length + 1) {
                    assertEquals(b.previous(), a.previous())
                    assertEquals(b.index, a.getIndex())
                    assertEquals(b.codePoint, a.getCodePoint())
                    assertEquals(b.value, a.getValue())
                }
            }
        }
    }
}
