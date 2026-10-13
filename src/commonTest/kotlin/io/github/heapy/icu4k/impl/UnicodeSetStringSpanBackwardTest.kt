package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.test.*

class UnicodeSetStringSpanBackwardTest {
    @Test fun backwardGreedyAndContainedChooseDifferentPaths() {
        val span = UnicodeSetStringSpan(UnicodeSet(), arrayListOf("ba", "cba", "dc"), UnicodeSetStringSpan.ALL)
        assertEquals(1, span.spanBack("dcba", 4, UnicodeSet.SpanCondition.SIMPLE))
        assertEquals(0, span.spanBack("dcba", 4, UnicodeSet.SpanCondition.CONTAINED))
    }
    @Test fun overlapsCodePointSpanAndHandlesLongSuffix() {
        val span = UnicodeSetStringSpan(UnicodeSet(65, 67), arrayListOf("ABx", "xAB", "x" + "A".repeat(260)), UnicodeSetStringSpan.ALL)
        assertEquals(1, span.spanBack("!ABxAB", 6, UnicodeSet.SpanCondition.CONTAINED))
        assertEquals(1, span.spanBack("!ABxAB", 6, UnicodeSet.SpanCondition.SIMPLE))
        assertEquals(1, span.spanBack("!x" + "A".repeat(260), 262, UnicodeSet.SpanCondition.CONTAINED))
        assertEquals(0, span.spanBack("", 0, UnicodeSet.SpanCondition.CONTAINED))
    }
}
