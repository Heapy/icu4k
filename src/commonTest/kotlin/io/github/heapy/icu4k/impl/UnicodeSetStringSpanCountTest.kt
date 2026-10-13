package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.util.OutputInt
import kotlin.test.*

class UnicodeSetStringSpanCountTest {
    @Test fun containedCanReachFurtherThanGreedySimple() {
        val span = UnicodeSetStringSpan(UnicodeSet(), arrayListOf("ab", "abc", "cd"), UnicodeSetStringSpan.ALL)
        val count = OutputInt()
        assertEquals(3, span.spanAndCount("abcd", 0, UnicodeSet.SpanCondition.SIMPLE, count)); assertEquals(1, count.value)
        assertEquals(4, span.spanAndCount("abcd", 0, UnicodeSet.SpanCondition.CONTAINED, count)); assertEquals(2, count.value)
    }
    @Test fun fullyContainedStringsReduceElementCount() {
        val span = UnicodeSetStringSpan(UnicodeSet(65, 67), arrayListOf("AB", "ABC"), UnicodeSetStringSpan.ALL)
        assertFalse(span.needsStringSpanUTF16())
        val count = OutputInt()
        assertEquals(6, span.spanAndCount("ABCABC!", 0, UnicodeSet.SpanCondition.CONTAINED, count)); assertEquals(2, count.value)
        assertEquals(6, span.spanAndCount("ABCABC!", 0, UnicodeSet.SpanCondition.SIMPLE, count)); assertEquals(2, count.value)
        assertEquals(0, span.spanAndCount("", 0, UnicodeSet.SpanCondition.CONTAINED, count)); assertEquals(0, count.value)
    }
}
