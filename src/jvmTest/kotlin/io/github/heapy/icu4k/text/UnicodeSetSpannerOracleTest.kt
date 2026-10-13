package io.github.heapy.icu4k.text

import com.ibm.icu.text.UnicodeSet as JavaSet
import com.ibm.icu.text.UnicodeSetSpanner as JavaSpanner
import kotlin.random.Random
import kotlin.test.*

class UnicodeSetSpannerOracleTest {
    @Test fun operationsMatchOriginalWithOverlapsAndMalformedText() {
        val random = Random(277)
        repeat(180) { seed ->
            val js = JavaSet().add(65, 67).add(0x1f600); val ks = UnicodeSet(65, 67).add(0x1f600)
            if (seed % 3 == 0) { js.complement(); ks.complement() }
            for (s in listOf("", "ab", "abc", "cd", "AB", "😀x", "x😀")) if (random.nextBoolean()) { js.add(s); ks.add(s) }
            if (seed % 2 == 0) { js.freeze(); ks.freeze() }
            val j = JavaSpanner(js); val k = UnicodeSetSpanner(ks)
            for (text in listOf("", "abcd!abc", "ABCx😀x😀ABC", "!xy!", CharArray(15) { random.nextInt(0x10000).toChar() }.concatToString())) {
                assertEquals(j.countIn(text), k.countIn(text))
                assertEquals(j.replaceFrom(text, "x"), k.replaceFrom(text, "x"))
                assertEquals(j.deleteFrom(text), k.deleteFrom(text))
                for (condition in UnicodeSet.SpanCondition.entries) {
                    val jc = JavaSet.SpanCondition.valueOf(condition.name)
                    assertEquals(j.deleteFrom(text, jc), k.deleteFrom(text, condition))
                    for (method in UnicodeSetSpanner.CountMethod.entries) {
                        val jm = JavaSpanner.CountMethod.valueOf(method.name)
                        assertEquals(j.countIn(text, jm, jc), k.countIn(text, method, condition))
                        for (replacement in listOf("", "x", "😀")) assertEquals(j.replaceFrom(text, replacement, jm, jc), k.replaceFrom(text, replacement, method, condition))
                    }
                    for (option in UnicodeSetSpanner.TrimOption.entries) {
                        val expected = runCatching { j.trim(text, JavaSpanner.TrimOption.valueOf(option.name), jc).toString() }
                        val actual = runCatching { k.trim(text, option, condition).toString() }
                        assertEquals(expected.getOrNull(), actual.getOrNull())
                        assertEquals(expected.isSuccess, actual.isSuccess)
                    }
                }
            }
        }
    }
}
