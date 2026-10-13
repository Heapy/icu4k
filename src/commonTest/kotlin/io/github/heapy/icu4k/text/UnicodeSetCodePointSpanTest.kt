package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.OutputInt
import kotlin.test.*

class UnicodeSetCodePointSpanTest {
    @Test fun countsPairsAndTreatsSplitSurrogatesAsUnits() {
        val set = UnicodeSet(65, 90).add(0x1f600)
        val text = "AB😀!"
        val count = OutputInt()
        assertEquals(4, set.spanCodePointsAndCount(text, 0, UnicodeSet.SpanCondition.CONTAINED, count))
        assertEquals(3, count.value)
        assertEquals(0, set.spanCodePointsBack(text, 4, UnicodeSet.SpanCondition.CONTAINED))
        assertEquals(4, set.spanCodePointsBack(text, 5, UnicodeSet.SpanCondition.NOT_CONTAINED))
        assertEquals(3, set.spanCodePointsAndCount(text, 3, UnicodeSet.SpanCondition.CONTAINED, count))
        assertEquals(0, count.value)
    }
}
