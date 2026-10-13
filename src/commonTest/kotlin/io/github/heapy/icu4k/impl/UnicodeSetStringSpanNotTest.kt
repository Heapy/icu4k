package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.util.OutputInt
import kotlin.test.*

class UnicodeSetStringSpanNotTest {
    @Test fun skipsFalseStringStartsAndStopsAtActualElement() {
        val span = UnicodeSetStringSpan(UnicodeSet().add(65), arrayListOf("ab", "abc", "😀x"), UnicodeSetStringSpan.ALL)
        val count = OutputInt(99)
        assertEquals(3, span.spanNot("xayab", 0, count)); assertEquals(3, count.value)
        assertEquals(2, span.spanNot("xx😀x", 0, count)); assertEquals(2, count.value)
        assertEquals(4, span.spanNot("x😀y", 0, count)); assertEquals(3, count.value)
        assertEquals(2, span.spanNotBack("abyy", 4))
        assertEquals(0, span.spanNotBack("xyz", 3))
        count.value = 99
        assertEquals(0, span.spanNot("", 0, count)); assertEquals(99, count.value)
    }
}
