// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class RuleBasedCollatorTest {
    @Test fun publicRuleApiCloneFreezeAndKeys() {
        val c=RuleBasedCollator("&c<ch<d&a<ä")
        assertEquals(-1,c.compare("c","ch")); assertEquals(-1,c.compare("ch","d"))
        assertEquals(c.compare("c","ch"),c.getCollationKey("c")!!.compareTo(c.getCollationKey("ch")))
        assertNull(c.getLocale(ULocale.VALID_LOCALE)); assertNull(c.getLocale(ULocale.ACTUAL_LOCALE))
        val clone=c.clone(); assertNotSame(c,clone); assertEquals(c,clone)
        clone.setNumericCollation(true)
        assertFalse(c.getNumericCollation()); assertTrue(clone.getNumericCollation())
        assertEquals(-1,c.compare("10","2")); assertEquals(1,clone.compare("10","2"))
        assertSame(c,c.freeze()); assertSame(c,c.clone())
        val thaw=c.cloneAsThawed(); assertNotSame(c,thaw); assertFalse(thaw.isFrozen()); assertEquals(c,thaw)
        assertFailsWith<UnsupportedOperationException> { c.setStrength(0) }
        thaw.setStrength(0); assertTrue(thaw.equals("A","a"))
        assertFailsWith<IllegalArgumentException> { RuleBasedCollator(null) }
    }
    @Test fun publicCeCursorTextAdaptersAndSafeOffsets() {
        val c=RuleBasedCollator("&c<ch<cu&a<æ/e")
        val it=c.getCollationElementIterator("chu æ😀")
        val forward=buildList { while(true) { val ce=it.next(); if(ce==CollationElementIterator.NULLORDER) break; add(ce) } }
        it.reset()
        val backwards=buildList { while(true) { val ce=it.previous(); if(ce==CollationElementIterator.NULLORDER) break; add(ce) } }
        assertEquals(forward.reversed(),backwards)
        it.setOffset(1); assertEquals(0,it.getOffset())
        it.setText(UCharacterIterator.getInstance("chu æ😀"))
        assertSame(c,it.getRuleBasedCollator())
        assertTrue(it.getMaxExpansion(0xc0)>=2)
        val other=c.getCollationElementIterator(StringCharacterIterator("chu æ😀"))
        repeat(forward.size) { _ -> assertEquals(it.next(),other.next()) }
        assertEquals(0x1234,CollationElementIterator.primaryOrder(0x12345678))
    }
    @Test fun nullableArrayAndVarargReordering() {
        val c=RuleBasedCollator("")
        c.setReorderCodes(17,25); assertContentEquals(intArrayOf(17,25),c.getReorderCodes())
        c.setReorderCodes(null); assertTrue(c.getReorderCodes().isEmpty())
        c.setReorderCodes(intArrayOf(25,17)); assertContentEquals(intArrayOf(25,17),c.getReorderCodes())
        c.setReorderCodes(); assertTrue(c.getReorderCodes().isEmpty())
        assertTrue(Collator.getEquivalentReorderCodes(17).contains(17))
        val comparator: Comparator<Any?> = c
        assertEquals(c.compare("a","b"),comparator.compare(StringBuilder("a"),StringBuilder("b")))
        assertFailsWith<ClassCastException> { comparator.compare(1,"b") }
    }
}
