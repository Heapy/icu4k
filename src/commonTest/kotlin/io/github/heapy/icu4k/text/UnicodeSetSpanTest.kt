package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.OutputInt
import kotlin.test.*

class UnicodeSetSpanTest {
    @Test fun frozenStringsAndThawedCopiesKeepSpanSemantics() {
        val set = UnicodeSet().add("ab").add("abc").add("cd")
        repeat(2) {
            assertEquals(4, set.span("abcd!", UnicodeSet.SpanCondition.CONTAINED))
            assertEquals(3, set.span("abcd!", UnicodeSet.SpanCondition.SIMPLE))
            assertEquals(1, set.spanBack("!abcd", UnicodeSet.SpanCondition.CONTAINED))
            set.freeze()
        }
        assertSame(set, set.clone())
        val copy = set.cloneAsThawed().add("!")
        assertEquals(5, copy.span("abcd!", UnicodeSet.SpanCondition.CONTAINED))
        assertEquals(4, set.span("abcd!", UnicodeSet.SpanCondition.CONTAINED))
        assertFailsWith<UnsupportedOperationException> { set.clear() }
    }
    @Test fun fullyContainedStringsStillReduceCountsAfterFreezing() {
        val set = UnicodeSet(65, 67).add("ABC").freeze()
        val count = OutputInt(91)
        assertEquals(6, set.spanAndCount("ABCABC!", 0, UnicodeSet.SpanCondition.CONTAINED, count))
        assertEquals(2, count.value)
        assertEquals(7, set.spanAndCount("ABCABC!", 99, UnicodeSet.SpanCondition.CONTAINED, count))
        assertEquals(2, count.value)
        assertFailsWith<IllegalArgumentException> { set.spanAndCount("", 0, UnicodeSet.SpanCondition.CONTAINED, null) }
    }
}
