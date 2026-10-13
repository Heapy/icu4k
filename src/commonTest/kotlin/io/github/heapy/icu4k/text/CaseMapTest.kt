package io.github.heapy.icu4k.text

import kotlin.test.*

class CaseMapTest {
    @Test fun immutableOptionsCommuteAndRetainCachedInstances() {
        val base = CaseMap.fold()
        assertSame(base, CaseMap.fold())
        assertSame(base.turkic(), base.turkic().turkic())
        assertSame(base.omitUnchangedText(), base.omitUnchangedText().omitUnchangedText())
        assertSame(base.turkic().omitUnchangedText(), base.omitUnchangedText().turkic())
        assertEquals("ii\u0307ıi", base.apply("Iİıi"))
        assertEquals("ıiıi", base.turkic().apply("Iİıi"))
        assertEquals("sss", base.omitUnchangedText().apply("Straße"))
        assertEquals("strasse", base.apply("Straße"))
    }
    @Test fun appendAndEditsReset() {
        val dest = StringBuilder("prefix")
        val edits = Edits().apply { addReplace(10, 20) }
        assertSame(dest, CaseMap.fold().omitUnchangedText().apply("Straße", dest, edits))
        assertEquals("prefixsss", dest.toString())
        assertEquals(1, edits.lengthDelta())
        assertEquals(2, edits.numberOfChanges())
        val iter = edits.getFineIterator()
        assertTrue(iter.next()); assertEquals(1, iter.oldLength()); assertTrue(iter.hasChange())
        assertTrue(iter.next()); assertEquals(3, iter.oldLength()); assertFalse(iter.hasChange())
        assertTrue(iter.next()); assertEquals(1, iter.oldLength()); assertEquals(2, iter.newLength())
        assertTrue(iter.next()); assertEquals(1, iter.oldLength()); assertFalse(iter.hasChange())
        assertFalse(iter.next())
        assertEquals("strasse", CaseMap.fold().apply(StringBuilder("Straße"), StringBuilder(), null).toString())
    }
}
