package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.CharacterIterator
import kotlin.test.*

class CSCharacterIteratorTest {
    @Test fun retainedTextCloneAndEmptyCursor() {
        val text = StringBuilder("A😀")
        val iterator = CSCharacterIterator(text)
        assertEquals('A', iterator.first())
        assertEquals(0xd83d.toChar(), iterator.next())
        val copy = iterator.clone()
        iterator.last()
        assertEquals(1, copy.getIndex())
        text[0] = 'B'
        assertEquals('B', copy.first())
        assertEquals(CharacterIterator.DONE, iterator.setIndex(3))
        assertEquals(0xde00.toChar(), iterator.previous())
        val empty = CSCharacterIterator("")
        assertEquals(CharacterIterator.DONE, empty.last())
        assertEquals(0, empty.getIndex())
        assertFailsWith<NullPointerException> { CSCharacterIterator(null) }
        assertFailsWith<IllegalArgumentException> { copy.setIndex(4) }
    }
}
