package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.RBBIDataWrapper
import kotlin.test.*

class RBBITableBuilderTest {
    @Test fun buildsTaggedLookAheadChainingAndSafeTransitions() {
        val result = RBBIRuleParseResult("!!chain; !!lookAheadHardBreak; a/b{42}; bc{100};.;")
        val scanner = RBBIRuleScanner(result); scanner.parse()
        val sets = RBBISetBuilder(result); sets.buildRanges()
        val builder = RBBITableBuilder(result, sets, scanner.numRules())
        builder.buildForwardTable()
        val table = builder.exportTable()
        assertTrue(table.fFlags and RBBIDataWrapper.RBBI_LOOKAHEAD_HARD_BREAK != 0)
        assertTrue(table.fLookAheadResultsSize > 0)
        assertTrue(builder.fDStates.any { it.fAccepting > 1 })
        assertTrue(builder.fDStates.any { it.fLookAhead > 1 })
        assertTrue(result.fRuleStatusVals.containsAll(listOf(42, 100)))
        builder.buildSafeReverseTable()
        assertTrue(builder.exportSafeTable().fNumStates >= 2)
        assertEquals(builder.getTableSize(), table.toBinary().size)
        assertEquals(builder.getSafeTableSize(), builder.exportSafeTable().toBinary().size)
    }
    @Test fun absentDirectionHasNoForwardTable() {
        val result = RBBIRuleParseResult("a;")
        val scanner = RBBIRuleScanner(result); scanner.parse()
        val sets = RBBISetBuilder(result); sets.buildRanges()
        val builder = RBBITableBuilder(result, sets, scanner.numRules(), 1)
        builder.buildForwardTable()
        assertEquals(0, builder.getTableSize())
        assertEquals(0, builder.exportTable().fNumStates)
        assertEquals(0, builder.getSafeTableSize())
    }
}
