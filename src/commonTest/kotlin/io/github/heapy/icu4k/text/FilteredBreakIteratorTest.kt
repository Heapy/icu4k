package io.github.heapy.icu4k.text

import kotlin.test.*

class FilteredBreakIteratorTest {
    @Test fun suppressesAbbreviationsAndRetainsIndependentWrappedSnapshots() {
        val builder = FilteredBreakIteratorBuilder.getEmptyInstance()
        val rules = "[a-zA-Z]+[.] ' '*;.;"
        val empty = RuleBasedBreakIterator(rules)
        assertSame(empty, builder.wrapIteratorWithFilter(empty))
        assertTrue(builder.suppressBreakAfter("Mr.")); assertFalse(builder.suppressBreakAfter("Mr."))
        val filtered = builder.wrapIteratorWithFilter(RuleBasedBreakIterator(rules))
        assertTrue(builder.unsuppressBreakAfter("Mr.")); assertFalse(builder.unsuppressBreakAfter("Mr."))
        filtered.setText("Mr. Smith. Go.")
        assertEquals(11, filtered.next()); assertEquals(14, filtered.next()); assertEquals(BreakIterator.DONE, filtered.next())
        assertEquals(11, filtered.previous()); assertEquals(0, filtered.previous())
        assertFalse(filtered.isBoundary(4))
        // Original isBoundary leaves the delegate on the suppressed boundary.
        assertEquals(4, filtered.current())
        val copy = filtered.clone()
        assertEquals(11, copy.next()); assertEquals(4, filtered.current())
    }
}
