package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.UnicodeSetStringSpan as JavaSpan
import com.ibm.icu.text.UnicodeSet as JavaSet
import com.ibm.icu.util.OutputInt as JavaOutput
import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.util.OutputInt
import kotlin.random.Random
import kotlin.test.*

@Suppress("DEPRECATION")
class UnicodeSetStringSpanCountOracleTest {
    @Test fun allCountModesMatchIncludingOverlappingPathsAndFullyContainedStrings() {
        val random = Random(371)
        repeat(300) { seed ->
            val jset = JavaSet().add(65, 67).add(0x1f600)
            val kset = UnicodeSet().add(65, 67).add(0x1f600)
            if (seed % 3 == 0) { jset.complement(); kset.complement() }
            val strings = arrayListOf("", "ab", "abc", "cd", "bc", "AB", "ABC", "😀x", "x😀", "😀😀")
            repeat(5) { strings.add(CharArray(random.nextInt(1, 8)) { if (random.nextBoolean()) random.nextInt(65, 125).toChar() else random.nextInt(0xd800, 0xe000).toChar() }.concatToString()) }
            val texts = listOf("", "abcd", "ABCABC!", "xxabcab", "😀x😀😀", "AB".repeat(30), CharArray(35) {
                when (random.nextInt(3)) { 0 -> random.nextInt(65, 125).toChar(); 1 -> random.nextInt(0xd800, 0xe000).toChar(); else -> 0xffff.toChar() }
            }.concatToString())
            for (which in listOf(127, 98)) {
                val j = JavaSpan(jset, ArrayList(strings), which)
                val k = UnicodeSetStringSpan(kset, ArrayList(strings), which)
                for (text in texts) for (condition in UnicodeSet.SpanCondition.entries) {
                    if (which == 98 && condition == UnicodeSet.SpanCondition.NOT_CONTAINED) continue
                    for (start in 0..text.length) {
                        val jo = JavaOutput(91); val ko = OutputInt(91)
                        assertEquals(j.spanAndCount(text, start, JavaSet.SpanCondition.valueOf(condition.name), jo), k.spanAndCount(text, start, condition, ko), "$seed $which $condition $start")
                        assertEquals(jo.value, ko.value, "$seed $condition $start")
                    }
                }
            }
        }
    }
    @Test fun concurrentCountCallsDoNotShareScratch() {
        val span = UnicodeSetStringSpan(UnicodeSet().add(65, 67), arrayListOf("AB", "ABC", "BC", "CD"), UnicodeSetStringSpan.ALL)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
        try {
            val tasks = List(4) {
                pool.submit(java.util.concurrent.Callable {
                    repeat(200) {
                        val count = OutputInt()
                        assertEquals(150, span.spanAndCount("ABC".repeat(50), 0, UnicodeSet.SpanCondition.CONTAINED, count))
                        assertEquals(50, count.value)
                    }
                })
            }
            tasks.forEach { it.get() }
        } finally { pool.shutdownNow() }
    }
}
