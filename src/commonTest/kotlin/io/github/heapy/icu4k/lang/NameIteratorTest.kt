package io.github.heapy.icu4k.lang

import io.github.heapy.icu4k.util.ValueIterator
import kotlin.test.*

class NameIteratorTest {
    @Test fun rangesResetAndUnchangedExhaustedResult() {
        val iterator = UCharacter.getNameIterator()
        val result = ValueIterator.Element()
        iterator.setRange(65, 67)
        assertTrue(iterator.next(result)); assertEquals(65, result.integer)
        assertEquals("LATIN CAPITAL LETTER A", result.value)
        assertTrue(iterator.next(result)); assertEquals(66, result.integer)
        assertFalse(iterator.next(result)); assertEquals(66, result.integer)
        iterator.reset()
        assertTrue(iterator.next(result)); assertEquals(65, result.integer)
        assertFailsWith<IllegalArgumentException> { iterator.setRange(1, 1) }
        val extended = UCharacter.getExtendedNameIterator()
        extended.setRange(0, 1)
        assertTrue(extended.next(result)); assertEquals("<control-0000>", result.value)
        assertFalse(extended.next(result))
        val empty = UCharacter.getName1_0Iterator()
        empty.setRange(2, 1)
        assertFalse(empty.next(result))
        assertEquals("<control-0000>", result.value)
    }
}
