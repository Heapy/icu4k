package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.StringCharacterIterator
import kotlin.test.*

class RBBIRuleEngineTest {
    @Test fun wordRulesReturnBoundariesAndStatuses() {
        val data = RBBIDataWrapper.get(ICUBinary.getRequiredData("brkitr/word.brk"))
        val engine = RBBIRuleEngine(data, StringCharacterIterator("Hi 42!"))
        val positions = mutableListOf<Int>()
        val statuses = mutableListOf<Int>()
        while (true) {
            val next = engine.handleNext()
            if (next < 0) break
            positions.add(next)
            val index = engine.ruleStatusIndex
            statuses.add(data.fStatusTable[index + data.fStatusTable[index]])
        }
        assertEquals(listOf(2, 3, 5, 6), positions)
        assertEquals(listOf(200, 0, 100, 0), statuses)
        assertTrue(engine.done)
        assertEquals(-1, engine.handleSafePrevious(0))
        assertTrue(engine.handleSafePrevious(6) in 0..5)
    }
    @Test fun graphemeRulesHandleEmojiCombiningAndCrLf() {
        val data = RBBIDataWrapper.get(ICUBinary.getRequiredData("brkitr/char.brk"))
        val text = "A\u0301👩‍👩‍👧‍👦\r\n"
        val engine = RBBIRuleEngine(data, StringCharacterIterator(text))
        assertEquals(2, engine.handleNext())
        assertEquals(text.length - 2, engine.handleNext())
        assertEquals(text.length, engine.handleNext())
        assertEquals(-1, engine.handleNext())
    }
    @Test fun pinsIndicesAndAvoidsPairMiddleWithinRange() {
        val text = charArrayOf('x', 0xd800.toChar(), 0xdc00.toChar(), 'y').concatToString()
        val iter = StringCharacterIterator(text)
        assertEquals(0, RBBIRuleEngine.setIndex32(iter, -1))
        assertEquals(4, RBBIRuleEngine.setIndex32(iter, 99))
        assertEquals(1, RBBIRuleEngine.setIndex32(iter, 2))
        val sliced = StringCharacterIterator(text, 2, 4, 2)
        assertEquals(2, RBBIRuleEngine.setIndex32(sliced, 2))
    }
}
