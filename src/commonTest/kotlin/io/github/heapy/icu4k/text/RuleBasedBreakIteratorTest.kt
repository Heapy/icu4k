package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.ICUBinary
import kotlin.test.*

class RuleBasedBreakIteratorTest {
    @Test fun compiledWordIteratorIncludesTrainedDictionarySegmentation() {
        val iterator = RuleBasedBreakIterator.getInstanceFromCompiledRules(ICUBinary.getRequiredData("brkitr/word.brk"))
        iterator.setText("Hello ภาษาไทย 日本語 😀")
        assertEquals(0, iterator.first())
        assertEquals(5, iterator.next())
        assertEquals(BreakIterator.WORD_LETTER, iterator.getRuleStatus())
        val copy = iterator.clone()
        assertEquals(iterator.next(), copy.next())
        val end = iterator.last()
        assertEquals(iterator.getText().getEndIndex(), end)
        assertEquals(BreakIterator.DONE, iterator.next())
        assertTrue(iterator.isBoundary(0))
        assertFalse(iterator.isBoundary(end - 1)) // Inside the final supplementary pair.
        assertEquals(end, iterator.current())
        assertFailsWith<IndexOutOfBoundsException> { iterator.isBoundary(end + 1) }
        iterator.setText(StringBuilder("A😀B"))
        assertEquals(0, iterator.first())
        assertEquals(1, iterator.next())
        assertEquals(3, iterator.next())
        assertEquals(4, iterator.last())
    }
}
