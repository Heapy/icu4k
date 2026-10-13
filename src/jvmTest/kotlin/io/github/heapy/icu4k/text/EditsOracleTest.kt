package io.github.heapy.icu4k.text

import com.ibm.icu.text.Edits as JavaEdits
import kotlin.random.Random
import kotlin.test.*

class EditsOracleTest {
    private fun state(iterator: Edits.Iterator): List<Any> = listOf(iterator.hasChange(), iterator.oldLength(), iterator.newLength(), iterator.sourceIndex(), iterator.destinationIndex(), iterator.replacementIndex())
    private fun state(iterator: JavaEdits.Iterator): List<Any> = listOf(iterator.hasChange(), iterator.oldLength(), iterator.newLength(), iterator.sourceIndex(), iterator.destinationIndex(), iterator.replacementIndex())
    private fun javaIterator(edits: JavaEdits, mode: Int): JavaEdits.Iterator = when (mode) {
        0 -> edits.coarseIterator; 1 -> edits.coarseChangesIterator; 2 -> edits.fineIterator; else -> edits.fineChangesIterator
    }
    private fun commonIterator(edits: Edits, mode: Int): Edits.Iterator = when (mode) {
        0 -> edits.getCoarseIterator(); 1 -> edits.getCoarseChangesIterator(); 2 -> edits.getFineIterator(); else -> edits.getFineChangesIterator()
    }
    private fun compare(expected: JavaEdits, actual: Edits, seed: Int) {
        assertEquals(expected.lengthDelta(), actual.lengthDelta())
        assertEquals(expected.hasChanges(), actual.hasChanges())
        assertEquals(expected.numberOfChanges(), actual.numberOfChanges())
        for (mode in 0..3) {
            val j = javaIterator(expected, mode)
            val k = commonIterator(actual, mode)
            while (true) {
                val result = j.next()
                assertEquals(result, k.next(), "mode $mode")
                assertEquals(state(j), state(k), "mode $mode")
                // Compare the span diagnostic text, excluding platform identity prefixes.
                assertEquals(j.toString().substringAfter('{'), k.toString().substringAfter('{'))
                if (!result) break
            }
            val sourceLength = j.sourceIndex()
            val destinationLength = j.destinationIndex()
            val random = Random(seed + mode)
            repeat(500) {
                val index = random.nextInt(-2, maxOf(sourceLength, destinationLength) + 3)
                when (random.nextInt(5)) {
                    0 -> assertEquals(j.findSourceIndex(index), k.findSourceIndex(index))
                    1 -> assertEquals(j.findDestinationIndex(index), k.findDestinationIndex(index))
                    2 -> assertEquals(j.destinationIndexFromSourceIndex(index), k.destinationIndexFromSourceIndex(index))
                    3 -> assertEquals(j.sourceIndexFromDestinationIndex(index), k.sourceIndexFromDestinationIndex(index))
                    else -> assertEquals(j.next(), k.next())
                }
                assertEquals(state(j), state(k), "mode $mode, index $index, iteration $it")
            }
        }
    }

    @Test fun randomizedEditStreamsAndBidirectionalIndexSearchMatchIcu() {
        repeat(100) { seed ->
            val random = Random(seed)
            val expected = JavaEdits()
            val actual = Edits()
            repeat(200) {
                if (random.nextBoolean()) {
                    val length = random.nextInt(100)
                    expected.addUnchanged(length); actual.addUnchanged(length)
                } else {
                    val old = random.nextInt(100)
                    val new = random.nextInt(100)
                    expected.addReplace(old, new); actual.addReplace(old, new)
                }
            }
            compare(expected, actual, seed)
        }
    }

    @Test fun compressedAndMultiUnitLengthsMatchIcu() {
        val expected = JavaEdits()
        val actual = Edits()
        repeat(1300) { expected.addReplace(1, 2); actual.addReplace(1, 2) }
        for (length in listOf(0, 1, 6, 7, 60, 61, 0x7fff, 0x8000, 0xffff, 0x10000, 200_000)) {
            expected.addUnchanged(length); actual.addUnchanged(length)
            expected.addReplace(length, length + 1); actual.addReplace(length, length + 1)
            expected.addReplace(length + 1, length); actual.addReplace(length + 1, length)
        }
        compare(expected, actual, 200)
        val extreme = JavaEdits().apply { addReplace(Int.MAX_VALUE, Int.MAX_VALUE) }
        val common = Edits().apply { addReplace(Int.MAX_VALUE, Int.MAX_VALUE) }
        val j = extreme.fineIterator
        val k = common.getFineIterator()
        for (index in listOf(0, 0x3fffffff, 0x40000000, Int.MAX_VALUE - 1, 3, Int.MAX_VALUE)) {
            assertEquals(j.destinationIndexFromSourceIndex(index), k.destinationIndexFromSourceIndex(index))
            assertEquals(state(j), state(k))
        }
    }

    @Test fun composesRandomCompatibleEditStreamsAndAppendsToExistingEdits() {
        repeat(200) { seed ->
            val random = Random(300 + seed)
            val ab = JavaEdits(); val ac = Edits()
            var middleLength = 0
            repeat(100) {
                if (random.nextBoolean()) {
                    val unchanged = random.nextInt(20)
                    ab.addUnchanged(unchanged); ac.addUnchanged(unchanged)
                    middleLength += unchanged
                } else {
                    val old = random.nextInt(20)
                    val new = random.nextInt(20)
                    ab.addReplace(old, new); ac.addReplace(old, new)
                    middleLength += new
                }
            }
            val bc = JavaEdits(); val commonBC = Edits()
            var consumed = 0
            while (consumed < middleLength) {
                if (random.nextInt(4) == 0) {
                    val inserted = random.nextInt(20)
                    bc.addReplace(0, inserted); commonBC.addReplace(0, inserted)
                }
                val old = random.nextInt(1, minOf(30, middleLength - consumed) + 1)
                if (random.nextBoolean()) { bc.addUnchanged(old); commonBC.addUnchanged(old) }
                else { val new = random.nextInt(20); bc.addReplace(old, new); commonBC.addReplace(old, new) }
                consumed += old
            }
            val expected = JavaEdits().apply { addUnchanged(3); addReplace(2, 5) }.mergeAndAppend(ab, bc)
            val actual = Edits().apply { addUnchanged(3); addReplace(2, 5) }.mergeAndAppend(ac, commonBC)
            compare(expected, actual, seed)
        }
    }

    @Test fun overflowFailureStateAndResetMatchIcu() {
        for (insertion in listOf(false, true)) {
            val expected = JavaEdits()
            val actual = Edits()
            if (insertion) { expected.addReplace(0, Int.MAX_VALUE); actual.addReplace(0, Int.MAX_VALUE) }
            else { expected.addReplace(Int.MAX_VALUE, 0); actual.addReplace(Int.MAX_VALUE, 0) }
            // Deletion can reach MIN_VALUE once before the next overflow.
            if (!insertion) { expected.addReplace(1, 0); actual.addReplace(1, 0) }
            assertFailsWith<IndexOutOfBoundsException> { if (insertion) expected.addReplace(0, 1) else expected.addReplace(1, 0) }
            assertFailsWith<IndexOutOfBoundsException> { if (insertion) actual.addReplace(0, 1) else actual.addReplace(1, 0) }
            assertEquals(expected.lengthDelta(), actual.lengthDelta())
            assertEquals(expected.numberOfChanges(), actual.numberOfChanges())
            expected.reset(); actual.reset()
            compare(expected, actual, 0)
        }
    }
}
