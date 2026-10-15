// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class StringSearchTest {
    @Test fun primaryCanonicalMatchesAndOverlapBothDirections() {
        val c=RuleBasedCollator(""); c.setStrength(Collator.PRIMARY)
        val s=StringSearch("cafe",StringCharacterIterator("A CAFÉ cafe\u0301!"),c)
        assertEquals(2,s.first()); assertEquals(4,s.getMatchLength()); assertEquals("CAFÉ",s.getMatchedText())
        assertEquals(7,s.next()); assertEquals(5,s.getMatchLength()); assertEquals(-1,s.next())
        val overlap=StringSearch("ana",StringCharacterIterator("banana"),c)
        overlap.setOverlapping(true)
        assertEquals(1,overlap.next()); assertEquals(3,overlap.next()); assertEquals(-1,overlap.next())
        overlap.reset(); overlap.setOverlapping(true)
        assertEquals(3,overlap.previous()); assertEquals(1,overlap.previous()); assertEquals(-1,overlap.previous())
    }
    @Test fun expansionsContractionsWildcardAndIdentical() {
        val c=RuleBasedCollator("&c<ch")
        assertEquals(3,StringSearch("c",StringCharacterIterator("ch c"),c).first())
        val german=Collator.getInstance(ULocale("de@collation=phonebook")) as RuleBasedCollator
        german.setStrength(0)
        val expanded=StringSearch("ae",StringCharacterIterator("ä ae"),german)
        assertEquals(0,expanded.first()); assertEquals(1,expanded.getMatchLength()); assertEquals(2,expanded.next())
        val wildcard=StringSearch("e",StringCharacterIterator("é"),RuleBasedCollator(""))
        assertEquals(-1,wildcard.first())
        wildcard.reset(); wildcard.setElementComparisonType(SearchIterator.ElementComparisonType.PATTERN_BASE_WEIGHT_IS_WILDCARD)
        assertEquals(0,wildcard.next()); assertEquals(1,wildcard.getMatchLength())
        c.setStrength(Collator.IDENTICAL)
        val identical=StringSearch("é",StringCharacterIterator("e\u0301 é"),c)
        assertEquals(0,identical.first()); assertEquals(2,identical.getMatchLength()); assertEquals(3,identical.next())
    }
    @Test fun boundariesResetAndValidation() {
        val word=BreakIterator.getWordInstance(ULocale.ENGLISH)
        val s=StringSearch("cat",StringCharacterIterator("scatter cat"),RuleBasedCollator(""),word)
        assertEquals(8,s.first())
        s.setCanonical(true); s.setOverlapping(true); s.reset()
        assertFalse(s.isCanonical()); assertFalse(s.isOverlapping())
        assertEquals(SearchIterator.ElementComparisonType.STANDARD_ELEMENT_COMPARISON,s.getElementComparisonType())
        assertFailsWith<IllegalArgumentException> { s.setPattern("") }
        assertFailsWith<IllegalArgumentException> { s.setCollator(null) }
        val numeric=RuleBasedCollator(""); numeric.setNumericCollation(true)
        assertFailsWith<UnsupportedOperationException> { StringSearch("1",StringCharacterIterator("12"),numeric) }
        val empty=StringSearch("",StringCharacterIterator("abc"),RuleBasedCollator(""))
        assertEquals(-1,empty.first()); assertEquals(0,empty.getMatchStart()); assertEquals(0,empty.getMatchLength())
    }
}
