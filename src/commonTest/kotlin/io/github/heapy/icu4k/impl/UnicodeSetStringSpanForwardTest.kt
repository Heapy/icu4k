package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.test.*

class UnicodeSetStringSpanForwardTest {
    @Test fun forwardGreedyAndContainedChooseDifferentPaths() {
        val span = UnicodeSetStringSpan(UnicodeSet(), arrayListOf("ab", "abc", "cd"), UnicodeSetStringSpan.ALL)
        assertEquals(3, span.span("abcd", 0, UnicodeSet.SpanCondition.SIMPLE))
        assertEquals(4, span.span("abcd", 0, UnicodeSet.SpanCondition.CONTAINED))
    }
    @Test fun overlapsCodePointSpanAndHandlesLongPrefix() {
        val span = UnicodeSetStringSpan(UnicodeSet(65, 67), arrayListOf("ABx", "xAB", "A".repeat(260) + "x"), UnicodeSetStringSpan.ALL)
        assertEquals(5, span.span("ABxAB!", 0, UnicodeSet.SpanCondition.CONTAINED))
        assertEquals(5, span.span("ABxAB!", 0, UnicodeSet.SpanCondition.SIMPLE))
        assertEquals(261, span.span("A".repeat(260) + "x!", 0, UnicodeSet.SpanCondition.CONTAINED))
        assertEquals(0, span.span("", 0, UnicodeSet.SpanCondition.CONTAINED))
    }
}
