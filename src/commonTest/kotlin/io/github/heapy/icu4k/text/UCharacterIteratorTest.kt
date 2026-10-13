package io.github.heapy.icu4k.text

import kotlin.test.*

class UCharacterIteratorTest {
    @Test fun iteratesCodePointsWithoutConsumingLookahead() {
        val text = charArrayOf('a', 0xd800.toChar(), 0xdc00.toChar(), 0xd800.toChar(), 'b')
        val iterator = UCharacterIterator.getInstance(text)
        assertEquals('a'.code, iterator.nextCodePoint())
        assertEquals(0x10000, iterator.currentCodePoint())
        assertEquals(1, iterator.getIndex())
        assertEquals(0x10000, iterator.nextCodePoint())
        assertEquals(0xd800, iterator.nextCodePoint())
        assertEquals('b'.code, iterator.nextCodePoint())
        assertEquals(UCharacterIterator.DONE, iterator.nextCodePoint())
        assertEquals('b'.code, iterator.previousCodePoint())
        assertEquals(0xd800, iterator.previousCodePoint())
        assertEquals(0x10000, iterator.previousCodePoint())
        iterator.setIndex(2)
        assertEquals(0xdc00, iterator.currentCodePoint())
        assertEquals(0xdc00, iterator.nextCodePoint())
    }

    @Test fun clonesCursorAndSharesStorage() {
        val builder = StringBuilder("abc")
        val original = UCharacterIterator.getInstance(builder)
        original.setIndex(1)
        val clone = original.clone()
        clone.next()
        assertEquals(1, original.getIndex())
        assertEquals(2, clone.getIndex())
        builder[1] = 'x'
        assertEquals('x'.code, original.current())
        assertEquals("axc", clone.getText())
    }

    @Test fun boundsAndSlices() {
        val array = charArrayOf(0xd800.toChar(), 0xdc00.toChar(), 'x')
        val slice = UCharacterIterator.getInstance(array, 1, 3)
        assertEquals(0xdc00, slice.nextCodePoint())
        assertEquals(2, slice.getLength())
        assertEquals(2, slice.moveIndex(100))
        assertFailsWith<IndexOutOfBoundsException> { slice.moveCodePointIndex(1) }
        assertEquals(2, slice.getIndex())
        assertFailsWith<IndexOutOfBoundsException> { slice.moveCodePointIndex(-3) }
        assertEquals(0, slice.getIndex())
        val target = CharArray(4) { '_' }
        assertEquals(2, slice.getText(target, 1))
        assertContentEquals(charArrayOf('_', 0xdc00.toChar(), 'x', '_'), target)
    }
}
