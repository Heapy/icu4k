package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.BinaryBuffer
import kotlin.test.*

class RuleCompilationTest {
    @Test fun publicCompilationAndStringConstructorShareBinarySemantics() {
        val rules = "[a-z]+{200};[0-9]+{100};.;"
        val bytes = RuleBasedBreakIterator.compileRules(rules)
        val fromString = RuleBasedBreakIterator(rules)
        val fromBytes = RuleBasedBreakIterator.getInstanceFromCompiledRules(BinaryBuffer(bytes))
        fromString.setText("abc12!"); fromBytes.setText("abc12!")
        val result = mutableListOf<Pair<Int, Int>>()
        while (true) {
            val next = fromString.next()
            assertEquals(next, fromBytes.next())
            assertEquals(fromString.getRuleStatus(), fromBytes.getRuleStatus())
            if (next == BreakIterator.DONE) break
            result.add(next to fromString.getRuleStatus())
        }
        assertEquals(listOf(3 to 200, 5 to 100, 6 to 0), result)
        assertEquals(fromString, fromString.clone())
        assertFailsWith<IllegalArgumentException> { RuleBasedBreakIterator("[];") }
    }
    @Test fun malformedSourceUnitsUseOriginalJavaUtf8ReplacementPolicy() {
        val rules = "\\" + "\ud800" + ";.;"
        val iterator = RuleBasedBreakIterator(rules)
        assertEquals("\\?;.;", iterator.toString())
        iterator.setText("\ud800x")
        assertEquals(1, iterator.next()); assertEquals(2, iterator.next())
    }
}
