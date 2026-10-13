package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.UnicodeSetStringSpan as JavaSpan
import com.ibm.icu.text.UnicodeSet as JavaSet
import com.ibm.icu.util.OutputInt as JavaOutput
import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.util.OutputInt
import kotlin.random.Random
import kotlin.test.*

@Suppress("DEPRECATION")
class UnicodeSetStringSpanNotOracleTest {
    @Test fun forwardCountsAndBackwardMatchesWithOverlapsAndMalformedUtf16() {
        val forward = JavaSpan::class.java.getDeclaredMethod("spanNot", CharSequence::class.java, Int::class.javaPrimitiveType, JavaOutput::class.java).apply { isAccessible = true }
        val back = JavaSpan::class.java.getDeclaredMethod("spanNotBack", CharSequence::class.java, Int::class.javaPrimitiveType).apply { isAccessible = true }
        val random = Random(398)
        repeat(300) {
            val jset = JavaSet().add(65, 67).add(0x1f600)
            val kset = UnicodeSet().add(65, 67).add(0x1f600)
            if (it % 3 == 0) { jset.complement(); kset.complement() }
            val strings = arrayListOf("ab", "abc", "bc", "xAB", "ABx", "😀x", "x😀", "")
            repeat(5) { strings.add(CharArray(random.nextInt(1, 6)) { random.nextInt(0xd800, 0xe000).toChar() }.concatToString()) }
            val fixtures = listOf("", "xxabcxab", "xxab", "abc", "ABxAB", "xx😀x", CharArray(30) {
                when (random.nextInt(3)) { 0 -> random.nextInt(65, 125).toChar(); 1 -> random.nextInt(0xd800, 0xe000).toChar(); else -> 0xffff.toChar() }
            }.concatToString())
            for (which in listOf(127, 33, 17, 97)) {
                val j = JavaSpan(jset, ArrayList(strings), which)
                val k = UnicodeSetStringSpan(kset, ArrayList(strings), which)
                for (text in fixtures) {
                    if (which != 17) for (start in 0..text.length) {
                        val jo = JavaOutput(91); val ko = OutputInt(91)
                        assertEquals(forward.invoke(j, text, start, jo), k.spanNot(text, start, ko), "$which $start")
                        assertEquals(jo.value, ko.value)
                        assertEquals(forward.invoke(j, text, start, null), k.spanNot(text, start, null))
                    }
                    if (which == 127 || which == 17) for (limit in 0..text.length) assertEquals(back.invoke(j, text, limit), k.spanNotBack(text, limit), "$which $limit")
                }
            }
        }
    }
}
