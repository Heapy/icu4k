package io.github.heapy.icu4k.text

import kotlin.test.*

class UnicodeSetSpannerTest {
    @Test fun countsElementsOrSpansAndRetainsSource() {
        val set = UnicodeSet.fromAll("ab").add("ab").freeze()
        val spanner = UnicodeSetSpanner(set)
        assertSame(set, spanner.getUnicodeSet())
        assertEquals(2, spanner.countIn("abab"))
        assertEquals(1, spanner.countIn("abab", UnicodeSetSpanner.CountMethod.WHOLE_SPAN))
        assertEquals("xx!", spanner.replaceFrom("abab!", "x"))
        assertEquals("!", spanner.deleteFrom("abab!"))
        assertEquals("cat", spanner.trim("abcatab").toString())
        val untouched = StringBuilder("cat")
        assertSame(untouched, spanner.trim(untouched))
        assertEquals(spanner, UnicodeSetSpanner(set.cloneAsThawed()))
        assertEquals(set.hashCode(), spanner.hashCode())
    }
}
