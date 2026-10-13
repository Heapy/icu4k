package io.github.heapy.icu4k.text

import kotlin.test.*

class RBBIRuleParseTableTest {
    @Test fun grammarPreservesLiteralRuleActionAndReturnStackSequence() {
        val input = "a;"
        var index = 0
        var state = 1
        val stack = ArrayList<Int>()
        val actions = ArrayList<Int>()
        val rows = RBBIRuleParseTable.gRuleParseStateTable
        while (state != 0) {
            assertTrue(actions.size < 20)
            val c = if (index < input.length) input[index].code else -1
            var row = state
            while (true) {
                val cc = rows[row].fCharClass
                if (cc == 255 || cc == c || (cc == 252 && c == -1) || (cc == 131 && c == 'a'.code)) break
                row++
            }
            val entry = rows[row]
            actions.add(entry.fAction)
            if (entry.fPushState != 0) stack.add(entry.fPushState)
            if (entry.fNextChar) index++
            state = if (entry.fNextState == 255) stack.removeAt(stack.lastIndex) else entry.fNextState
        }
        assertEquals(listOf(11, 18, 13, 8, 4, 13), actions)
        assertEquals(input.length, index)
        assertTrue(stack.isEmpty())
    }
}
