package io.github.heapy.icu4k.text

import kotlin.test.*

class EditsTest {
    private fun spans(iterator: Edits.Iterator): List<List<Int>> = buildList {
        while (iterator.next()) add(listOf(if (iterator.hasChange()) 1 else 0, iterator.oldLength(), iterator.newLength(), iterator.sourceIndex(), iterator.destinationIndex(), iterator.replacementIndex()))
    }
    @Test fun retainsFineSpansAndCombinesCoarseChanges() {
        val edits = Edits().apply {
            addUnchanged(2); addReplace(1, 2); addReplace(1, 2); addUnchanged(3); addReplace(0, 1); addReplace(2, 0)
        }
        assertEquals(1, edits.lengthDelta())
        assertEquals(4, edits.numberOfChanges())
        assertTrue(edits.hasChanges())
        assertEquals(listOf(listOf(0, 2, 2, 0, 0, 0), listOf(1, 1, 2, 2, 2, 0), listOf(1, 1, 2, 3, 4, 2),
            listOf(0, 3, 3, 4, 6, 4), listOf(1, 0, 1, 7, 9, 4), listOf(1, 2, 0, 7, 10, 5)), spans(edits.getFineIterator()))
        assertEquals(listOf(listOf(0, 2, 2, 0, 0, 0), listOf(1, 2, 4, 2, 2, 0), listOf(0, 3, 3, 4, 6, 4), listOf(1, 2, 1, 7, 9, 4)), spans(edits.getCoarseIterator()))
        assertEquals(4, spans(edits.getFineChangesIterator()).size)
        assertEquals(2, spans(edits.getCoarseChangesIterator()).size)
        val iterator = edits.getFineIterator()
        assertEquals(10, iterator.destinationIndexFromSourceIndex(8))
        assertEquals(2, iterator.destinationIndexFromSourceIndex(2))
        assertEquals(7, iterator.destinationIndexFromSourceIndex(5))
        assertEquals(3, iterator.sourceIndexFromDestinationIndex(3))
        assertEquals(0, iterator.destinationIndexFromSourceIndex(-1))
        assertEquals(10, iterator.destinationIndexFromSourceIndex(100))
    }

    @Test fun handlesCompressedSequencesAndLargeLengths() {
        val edits = Edits()
        repeat(1200) { edits.addReplace(1, 2) }
        edits.addUnchanged(9000)
        edits.addReplace(0x8000, 0x10000)
        val fine = edits.getFineIterator()
        assertTrue(fine.findSourceIndex(1199))
        assertEquals(2398, fine.destinationIndex())
        assertTrue(fine.findSourceIndex(600))
        assertEquals(1200, fine.destinationIndex())
        assertTrue(fine.findDestinationIndex(3))
        assertEquals(1, fine.sourceIndex())
        assertEquals(1202, spans(edits.getFineIterator()).size)
        val extreme = Edits().apply { addReplace(Int.MAX_VALUE, Int.MAX_VALUE) }
        val iterator = extreme.getFineIterator()
        assertTrue(iterator.next())
        assertEquals(Int.MAX_VALUE, iterator.oldLength())
        assertEquals(Int.MAX_VALUE, iterator.newLength())
        assertFalse(iterator.next())
    }

    @Test fun composesChangesAndKeepsInsertionBeforeDeletion() {
        val ab = Edits().apply { addUnchanged(2); addReplace(1, 2); addUnchanged(1) }
        val bc = Edits().apply { addUnchanged(3); addReplace(1, 0); addUnchanged(1) }
        val ac = Edits().mergeAndAppend(ab, bc)
        assertEquals(listOf(listOf(0, 2, 2, 0, 0, 0), listOf(1, 1, 1, 2, 2, 0), listOf(0, 1, 1, 3, 3, 1)), spans(ac.getFineIterator()))
        val deletion = Edits().apply { addUnchanged(1); addReplace(1, 0); addUnchanged(1) }
        val insertion = Edits().apply { addUnchanged(1); addReplace(0, 2); addUnchanged(1) }
        val result = spans(Edits().mergeAndAppend(deletion, insertion).getFineChangesIterator())
        assertEquals(listOf(listOf(1, 0, 2, 1, 1, 0), listOf(1, 1, 0, 1, 3, 2)), result)
        assertFailsWith<IllegalArgumentException> { Edits().mergeAndAppend(ab, Edits()) }
        assertFailsWith<IllegalArgumentException> { Edits().mergeAndAppend(Edits(), bc) }
    }

    @Test fun validatesLengthsAndDeltaOverflow() {
        val edits = Edits()
        assertFailsWith<IllegalArgumentException> { edits.addUnchanged(-1) }
        assertFailsWith<IllegalArgumentException> { edits.addReplace(0, -1) }
        edits.addReplace(0, 0)
        assertFalse(edits.hasChanges())
        edits.addReplace(0, Int.MAX_VALUE)
        assertFailsWith<IndexOutOfBoundsException> { edits.addReplace(0, 1) }
        edits.reset()
        assertEquals(0, edits.lengthDelta())
        assertFalse(edits.hasChanges())
        assertFalse(edits.getFineIterator().next())
    }
}
