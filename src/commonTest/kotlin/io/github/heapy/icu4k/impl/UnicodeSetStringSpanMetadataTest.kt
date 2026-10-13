package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.test.*

class UnicodeSetStringSpanMetadataTest {
    @Test fun preprocessesRelevantStringsAndCopies() {
        val strings = arrayListOf("", "AB", "ABx", "xAB")
        val span = UnicodeSetStringSpan(UnicodeSet(65, 90), strings, UnicodeSetStringSpan.ALL)
        assertEquals(listOf("AB", "ABx", "xAB"), strings)
        assertTrue(span.needsStringSpanUTF16())
        assertTrue(span.contains(65)); assertFalse(span.contains(120))
        assertTrue(UnicodeSetStringSpan(span, ArrayList(strings)).contains(65))
        val irrelevant = UnicodeSetStringSpan(UnicodeSet(65, 90), arrayListOf("AB"), UnicodeSetStringSpan.FWD_UTF16_CONTAINED)
        assertFalse(irrelevant.needsStringSpanUTF16())
    }
    @Test fun internalCodePointFreezeRejectsAllMutationsAndClonesThawed() {
        val set = UnicodeSet(65, 90).freezeCodePointSet()
        assertTrue(set.isFrozen()); assertSame(set, set.clone())
        val clone = set.cloneAsThawed()
        assertFalse(clone.isFrozen()); clone.add("ab")
        assertFalse(set.hasStrings())
        val mutations: List<() -> Unit> = listOf(
            { set.clear() }, { set.compact() }, { set.set(UnicodeSet()) }, { set.add(65) }, { set.add("ab") },
            { set.addAll(UnicodeSet()) }, { set.remove(65) }, { set.remove("ab") }, { set.retain(65) },
            { set.retain("ab") }, { set.complement() }, { set.complement(65) }, { set.complement("ab") },
            { set.retainAll(UnicodeSet()) }, { set.removeAll(UnicodeSet()) }, { set.complementAll(UnicodeSet()) },
            { set.removeAllStrings() },
        )
        for (mutate in mutations) assertFailsWith<UnsupportedOperationException> { mutate() }
    }
}
