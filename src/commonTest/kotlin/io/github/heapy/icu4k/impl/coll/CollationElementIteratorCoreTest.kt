// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.CloneNotSupportedException
import kotlin.test.*

internal fun elementCore(t: CollationTailoring = CollationRoot.getRoot(), owner: Any = t): CollationElementIteratorCore =
    CollationElementIteratorCore(owner, { t.data }, { t.settings!!.readOnly() }, { t.maxExpansions })

class CollationElementIteratorCoreTest {
    private fun forward(c: CollationElementIteratorCore): List<Int> = buildList {
        while (true) { val ce = c.next(); if (ce == -1) break; add(ce) }
    }
    @Test fun backwardsOffsetsContractionsAndDirectionErrors() {
        val t = CollationBuilder(CollationRoot.getRoot()).parseAndBuild("&c<ch<cu&a<æ/e")
        val c = elementCore(t)
        c.setText("chu æ😀")
        val ces = forward(c)
        assertFailsWith<IllegalStateException> { c.previous() }
        c.reset()
        val backwards = buildList { while (true) { val ce = c.previous(); if (ce == -1) break; add(ce); assertTrue(c.getOffset() in 0..7) } }
        assertEquals(ces.reversed(), backwards)
        assertFailsWith<IllegalStateException> { c.next() }
        c.setOffset(1); assertEquals(0, c.getOffset())
        c.setOffset(2); assertEquals(2, c.getOffset())
        c.setText(""); assertEquals(-1, c.next()); c.reset(); assertEquals(-1,c.previous())
    }
    @Test fun characterCursorAliasingAndUCursorCloneFallback() {
        val text = "aæ😀é\u0327"
        val c = elementCore()
        c.setText(text); val expected = forward(c)
        val source = StringCharacterIterator(text,2)
        c.setText(source)
        assertEquals(0, source.getIndex()) // CharacterIterator is used directly.
        assertEquals(expected, forward(c)); assertEquals(text.length,source.getIndex())
        val u = UCharacterIterator.getInstance(text)
        u.setIndex(2); c.setText(u)
        assertEquals(expected, forward(c)); assertEquals(2,u.getIndex())
        val noClone = object : UCharacterIterator() {
            private val delegate = UCharacterIterator.getInstance(text)
            override fun current() = delegate.current()
            override fun getLength() = delegate.getLength()
            override fun getIndex() = delegate.getIndex()
            override fun next() = delegate.next()
            override fun previous() = delegate.previous()
            override fun setIndex(index: Int) = delegate.setIndex(index)
            override fun getText(fillIn: CharArray, offset: Int) = delegate.getText(fillIn,offset)
            override fun clone(): UCharacterIterator = throw CloneNotSupportedException()
        }
        c.setText(noClone); assertEquals(expected,forward(c))
    }
    @Test fun expansionLookupAndHalfEncoding() {
        val map = CollationElementIteratorCore.computeMaxExpansions(CollationRoot.getData())
        assertTrue(map.isNotEmpty()); assertTrue(map.values.max() > 2)
        assertEquals(1,CollationElementIteratorCore.getMaxExpansion(map,0))
        assertEquals(2,CollationElementIteratorCore.getMaxExpansion(null,0xc0))
        assertEquals(9,CollationElementIteratorCore.getMaxExpansion(mapOf(0xc0 to 9),0xc0))
        assertEquals(0x1234,CollationElementIteratorCore.primaryOrder(0x12345678))
        assertEquals(0x56,CollationElementIteratorCore.secondaryOrder(0x12345678))
        assertEquals(0x78,CollationElementIteratorCore.tertiaryOrder(0x12345678))
    }
}
