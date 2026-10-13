package io.github.heapy.icu4k.text

import com.ibm.icu.text.UnicodeSet as JavaSet
import kotlin.random.Random
import kotlin.test.*

class UnicodeSetExtraOracleTest {
    @Test fun matchAtAndRepresentationsRetainOriginalBehavior() {
        val random = Random(432)
        repeat(600) {
            val j = JavaSet().add(65, 70); val k = UnicodeSet(65, 70)
            for (s in listOf("", "ab", "abc", "abcd", "xy", "😀x", "a-b")) if (random.nextBoolean()) { j.add(s); k.add(s) }
            assertEquals(j.toString(), k.toString())
            assertEquals(j.getRegexEquivalent(), k.getRegexEquivalent())
            for (text in listOf("abcd", "ab", "ABC", "xy!", "😀x", "!abc", "")) for (offset in 0..text.length) {
                val expected = runCatching { j.matchesAt(text, offset) }
                val actual = runCatching { k.matchesAt(text, offset) }
                assertEquals(expected.getOrNull(), actual.getOrNull(), "$text $offset")
                assertEquals(expected.isSuccess, actual.isSuccess)
            }
        }
    }
}
