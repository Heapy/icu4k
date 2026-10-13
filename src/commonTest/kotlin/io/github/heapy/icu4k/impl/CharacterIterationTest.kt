package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.CharacterIterator
import io.github.heapy.icu4k.text.StringCharacterIterator
import kotlin.test.*

class CharacterIterationTest {
    @Test fun distinguishesUffffAndRangeEnd() {
        val iter = StringCharacterIterator(charArrayOf(0xffff.toChar(), 0xd800.toChar(), 0xdc00.toChar()).concatToString())
        assertEquals(0xffff, CharacterIteration.current32(iter))
        assertEquals(0x10000, CharacterIteration.next32(iter))
        assertEquals(1, iter.getIndex())
        assertEquals(CharacterIteration.DONE32, CharacterIteration.next32(iter))
        assertEquals(3, iter.getIndex())
        assertEquals(0x10000, CharacterIteration.previous32(iter))
        assertEquals(0xffff, CharacterIteration.previous32(iter))
        assertEquals(CharacterIteration.DONE32, CharacterIteration.previous32(iter))
    }
    @Test fun rangeCanSplitPairAndCloneIsIndependent() {
        val text = charArrayOf('a', 0xd800.toChar(), 0xdc00.toChar(), 'z').concatToString()
        val iter = StringCharacterIterator(text, 1, 2, 1)
        assertEquals(0xd800, CharacterIteration.current32(iter))
        assertEquals(CharacterIteration.DONE32, CharacterIteration.next32(iter))
        val clone = iter.clone()
        assertEquals(0xd800.toChar(), clone.first())
        assertEquals(2, iter.getIndex())
        assertEquals(CharacterIterator.DONE, clone.previous())
        assertEquals(1, clone.getIndex())
        assertFailsWith<IllegalArgumentException> { iter.setIndex(0) }
        assertFailsWith<IllegalArgumentException> { StringCharacterIterator(text, 3, 2, 3) }
        val empty = StringCharacterIterator("")
        assertEquals(CharacterIterator.DONE, empty.last())
        assertEquals(0, empty.getIndex())
    }
}
