package io.github.heapy.icu4k.text

import com.ibm.icu.text.UnicodeSet as JavaSet
import com.ibm.icu.util.OutputInt as JavaCount
import io.github.heapy.icu4k.util.OutputInt
import kotlin.random.Random
import kotlin.test.*

class UnicodeSetSpanOracleTest {
    @Test fun publicDispatchAndFreezingMatchIcu() {
        val random = Random(908)
        repeat(180) { seed ->
            val j = JavaSet().add(65, 67).add(0x1f600)
            val k = UnicodeSet().add(65, 67).add(0x1f600)
            if (seed % 4 == 0) { j.complement(); k.complement() }
            val strings = when (seed % 3) {
                0 -> listOf("ab", "abc", "cd", "ABx", "xAB", "😀x", "x😀", "x" + "A".repeat(260))
                1 -> listOf("AB", "ABC", "😀😀")
                else -> emptyList()
            }
            strings.forEach { j.add(it); k.add(it) }
            val texts = listOf("abcd", "ABCxAB!", "😀x😀😀", "!x" + "A".repeat(260), CharArray(35) {
                when (random.nextInt(3)) { 0 -> random.nextInt(65, 125).toChar(); 1 -> random.nextInt(0xd800, 0xe000).toChar(); else -> 0xffff.toChar() }
            }.concatToString())
            repeat(2) { frozen ->
                if (frozen == 1) { j.freeze(); assertSame(k, k.freeze()); assertTrue(k.isFrozen()); assertSame(k, k.clone()); assertFalse(k.cloneAsThawed().isFrozen()); assertFailsWith<UnsupportedOperationException> { k.add(1) } }
                for (text in texts) for (condition in UnicodeSet.SpanCondition.entries) {
                    val jc = JavaSet.SpanCondition.valueOf(condition.name)
                    assertEquals(j.span(text, jc), k.span(text, condition))
                    assertEquals(j.spanBack(text, jc), k.spanBack(text, condition))
                    for (index in -2..text.length + 2) {
                        assertEquals(j.span(text, index, jc), k.span(text, index, condition), "$seed $frozen $condition $index forward")
                        assertEquals(j.spanBack(text, index, jc), k.spanBack(text, index, condition), "$seed $frozen $condition $index back")
                        val jo = JavaCount(91); val ko = OutputInt(91)
                        assertEquals(j.spanAndCount(text, index, jc, jo), k.spanAndCount(text, index, condition, ko))
                        assertEquals(jo.value, ko.value, "$seed $frozen $condition $index count")
                    }
                }
            }
        }
    }
}
