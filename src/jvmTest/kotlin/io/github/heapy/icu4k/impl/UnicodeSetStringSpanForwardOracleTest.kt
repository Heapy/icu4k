package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.UnicodeSetStringSpan as JavaSpan
import com.ibm.icu.text.UnicodeSet as JavaSet
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.random.Random
import kotlin.test.*

class UnicodeSetStringSpanForwardOracleTest {
    @Test fun overlappingForwardSpansMatchIcu() {
        val random = Random(894)
        repeat(300) { seed ->
            val jset = JavaSet().add(65, 67).add(0x1f600)
            val kset = UnicodeSet().add(65, 67).add(0x1f600)
            if (seed % 3 == 0) { jset.complement(); kset.complement() }
            val strings = arrayListOf("", "ab", "abc", "cd", "bc", "AB", "ABC", "ABx", "xAB", "😀x", "x😀", "😀😀", "A".repeat(260) + "x")
            repeat(10) { strings.add(CharArray(random.nextInt(1, 10)) { if (random.nextBoolean()) random.nextInt(65, 125).toChar() else random.nextInt(0xd800, 0xe000).toChar() }.concatToString()) }
            val texts = listOf("", "abcd", "ABCxAB!", "ABCABC!", "xxabcab", "😀x😀😀", "AB".repeat(30), "A".repeat(260) + "x!", CharArray(35) {
                when (random.nextInt(3)) { 0 -> random.nextInt(65, 125).toChar(); 1 -> random.nextInt(0xd800, 0xe000).toChar(); else -> 0xffff.toChar() }
            }.concatToString())
            for (which in listOf(127, 34)) {
                val j = JavaSpan(jset, ArrayList(strings), which)
                val k = UnicodeSetStringSpan(kset, ArrayList(strings), which)
                for (text in texts) for (condition in UnicodeSet.SpanCondition.entries) {
                    if (which == 34 && condition == UnicodeSet.SpanCondition.NOT_CONTAINED) continue
                    for (start in 0..text.length) assertEquals(j.span(text, start, JavaSet.SpanCondition.valueOf(condition.name)), k.span(text, start, condition), "$seed $which $condition $start")
                }
            }
        }
    }
}
