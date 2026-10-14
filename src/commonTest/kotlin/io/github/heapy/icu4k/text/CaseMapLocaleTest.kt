package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class CaseMapLocaleTest {
    @Test fun contextualCasingOptionsAndEdits() {
        assertEquals("ıi", CaseMap.toLower().apply(ULocale("tr"), "Iİ"))
        assertEquals("İI", CaseMap.toUpper().apply(ULocale("tr"), "iı"))
        assertEquals("ος", CaseMap.toLower().apply(ULocale.ROOT, "ΟΣ"))
        assertEquals("STRASSE", CaseMap.toUpper().apply(ULocale.ROOT, "Straße"))
        val dest = StringBuilder("prefix:"); val edits = Edits().also { it.addUnchanged(99) }
        assertSame(dest, CaseMap.toUpper().omitUnchangedText().apply(ULocale.ROOT, "Aßz", dest, edits))
        assertEquals("prefix:SSZ", dest.toString()); assertTrue(edits.hasChanges())
        assertSame(CaseMap.toLower().omitUnchangedText(), CaseMap.toLower().omitUnchangedText().omitUnchangedText())
        val failure = IllegalStateException("append")
        val broken = object : Appendable {
            override fun append(value: Char): Appendable = throw failure
            override fun append(value: CharSequence?): Appendable = throw failure
            override fun append(value: CharSequence?, start: Int, end: Int): Appendable = throw failure
        }
        assertSame(failure, assertFailsWith<IllegalStateException> { CaseMap.toUpper().apply(ULocale.ROOT, "a", broken, null) })
    }
    @Test fun nullableDestinationsAndSourceFailureRetainSourceSemantics() {
        assertNull(CaseMap.toLower().apply(ULocale.ROOT, "", null, null))
        assertNull(CaseMap.toUpper().omitUnchangedText().apply(ULocale.ROOT, "UNCHANGED", null, null))
        assertNull(CaseMap.fold().omitUnchangedText().apply("unchanged", null, null))
        assertFailsWith<NullPointerException> { CaseMap.toLower().apply(ULocale.ROOT, "A", null, null) }
        val edits = Edits().also { it.addReplace(1, 2) }
        assertFailsWith<NullPointerException> { CaseMap.toLower().apply(ULocale.ROOT, null, StringBuilder(), edits) }
        assertFalse(edits.hasChanges()); assertEquals(0, edits.lengthDelta())
    }

}
