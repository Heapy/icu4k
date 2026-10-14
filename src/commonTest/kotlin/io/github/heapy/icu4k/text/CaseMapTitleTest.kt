package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class CaseMapTitleTest {
    @Test fun titleOptionsAndLocaleRules() {
        assertEquals("Hello World", CaseMap.toTitle().apply(ULocale.ROOT, null, "hELLO wORLD"))
        assertEquals("Hello world", CaseMap.toTitle().wholeString().apply(ULocale.ROOT, null, "hELLO wORLD"))
        assertEquals("HELLO wORLD", CaseMap.toTitle().wholeString().noLowercase().apply(ULocale.ROOT, null, "hELLO wORLD"))
        assertEquals("IJsselmeer", CaseMap.toTitle().wholeString().apply(ULocale("nl"), null, "ijsselmeer"))
        assertEquals("İstanbul", CaseMap.toTitle().wholeString().apply(ULocale("tr"), null, "istanbul"))
        assertFailsWith<IllegalArgumentException> { CaseMap.toTitle().wholeString().sentences() }
        assertFailsWith<IllegalArgumentException> { CaseMap.toTitle().noBreakAdjustment().adjustToCased() }
        assertFailsWith<IllegalArgumentException> { CaseMap.toTitle().wholeString().apply(ULocale.ROOT, BreakIterator.getWordInstance(ULocale.ROOT), "abc") }
        val edits = Edits().also { it.addReplace(1, 3) }
        assertFailsWith<NullPointerException> { CaseMap.toTitle().wholeString().apply(ULocale.ROOT, null, null, StringBuilder(), edits) }
        assertTrue(edits.hasChanges())
        assertNull(CaseMap.toTitle().wholeString().omitUnchangedText().apply(ULocale.ROOT, null, "Unchanged", null, edits))
        assertFalse(edits.hasChanges())
    }
}
