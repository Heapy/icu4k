// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.text.UCharacterIterator
import kotlin.test.*

class IterCollationIteratorTest {
    @Test fun rawCursorIsSharedAndSurrogatesDoNotConsumeFollowingUnits() {
        val raw = UCharacterIterator.getInstance(iteratorMalformedText("😀", 'x'))
        val it = IterCollationIterator(iteratorFixtureData(), false, raw)
        for (cp in listOf(0x1f600,0xd800,0x78,0xdc00,-1)) assertEquals(cp,it.nextCodePoint())
        assertEquals(raw.getIndex(),it.getOffset())
        for (cp in listOf(0xdc00,0x78,0xd800,0x1f600,-1)) assertEquals(cp,it.previousCodePoint())
        raw.setIndex(2); assertEquals(2,it.getOffset()); assertEquals(0xd800,it.nextCodePoint())
        it.resetToOffset(0); assertEquals(0,raw.getIndex())
    }
    @Test fun ceStreamsMatchTheUTF16Cursors() {
        val d = iteratorFixtureData()
        for (numeric in listOf(false,true)) for (text in listOf("abc abx xp yxp", "1230001042490", "a\u0323\u0301", "a\u0315\u0300b", "é\u0323", "\u0f73", "𝅗𝅥𝅥\u0300", iteratorMalformedText("😀", 'x'), "g".repeat(100))) {
            for (fcd in listOf(false,true)) {
                val raw = UCharacterIterator.getInstance(text)
                val it: CollationIterator = if (fcd) FCDIterCollationIterator(d,numeric,raw,0) else IterCollationIterator(d,numeric,raw)
                val utf: CollationIterator = if (fcd) FCDUTF16CollationIterator(d,numeric,text,0) else UTF16CollationIterator(d,numeric,text,0)
                while (true) { val ce = utf.nextCE(); assertEquals(ce,it.nextCE()); assertEquals(utf.getOffset(),it.getOffset()); if (ce == Collation.NO_CE) break }
                val a = UVector32(); val b = UVector32()
                while (true) { val ce = utf.previousCE(a); assertEquals(ce,it.previousCE(b)); assertEquals(utf.getOffset(),it.getOffset()); if (ce == Collation.NO_CE) break }
            }
        }
    }
    @Test fun normalizedOffsetsAndDirectionChanges() {
        val raw = UCharacterIterator.getInstance("a\u0315\u0300b")
        val it = FCDIterCollationIterator(iteratorFixtureData(),false,raw,0)
        assertEquals(0x61,it.nextCodePoint()); assertEquals(0x300,it.nextCodePoint())
        assertEquals(3,it.getOffset()); assertEquals(3,raw.getIndex())
        assertEquals(0x300,it.previousCodePoint()); assertEquals(1,it.getOffset()); assertEquals(3,raw.getIndex())
        assertEquals(0x61,it.previousCodePoint()); assertEquals(0,raw.getIndex())
        assertEquals(0x61,it.nextCodePoint()); assertEquals(0x300,it.nextCodePoint()); assertEquals(0x315,it.nextCodePoint()); assertEquals(0x62,it.nextCodePoint())
        it.resetToOffset(4)
        for ((cp,offset) in listOf(0x62 to 3,0x315 to 3,0x300 to 1,0x61 to 0,-1 to 0)) { assertEquals(cp,it.previousCodePoint()); assertEquals(offset,it.getOffset()) }
    }
    @Test fun nullIteratorFailsLazilyAndResetClearsCEsBeforeIndexFailure() {
        val d = iteratorFixtureData()
        val empty = IterCollationIterator(d,false,null)
        assertEquals(0,empty.getCEsLength()); assertFailsWith<NullPointerException> { empty.getOffset() }
        assertFailsWith<NullPointerException> { empty.nextCE() }; assertEquals(1,empty.getCEsLength())
        assertFailsWith<NullPointerException> { empty.resetToOffset(0) }; assertEquals(0,empty.getCEsLength())
        val raw = UCharacterIterator.getInstance("g")
        val it = IterCollationIterator(d,false,raw)
        it.nextCE(); assertEquals(2,it.getCEsLength())
        assertFailsWith<IndexOutOfBoundsException> { it.resetToOffset(2) }
        assertEquals(0,it.getCEsLength()); assertEquals(1,it.getOffset())
    }
}
