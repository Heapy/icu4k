package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.SimpleFormatterImpl
import kotlin.test.*

class SimpleFormatterTest {
    @Test fun numberedArgumentsAndQuoting() {
        val formatter = SimpleFormatter.compile("{1} '{born}' in {0}")
        assertEquals(2, formatter.getArgumentLimit())
        assertEquals("paul {born} in england", formatter.format("england", "paul"))
        assertEquals("{1} {born} in {0}", formatter.toString())
        assertEquals(" {born} in ", formatter.getTextWithNoArguments())
        assertEquals("a'b", SimpleFormatter.compile("a''b").format())
        assertEquals("null", SimpleFormatter.compile("{0}").format(null))
        assertEquals("literal", SimpleFormatter.compile("literal").formatAndAppendValues(StringBuilder(), null, null).toString())
        assertFailsWith<Exception> { SimpleFormatter.compile("{255}").getTextWithNoArguments() }
        for (pattern in listOf("{", "{01}", "{256}", "{ 0}", "{0,x}")) assertFailsWith<IllegalArgumentException> { SimpleFormatter.compile(pattern) }
    }
    @Test fun replacementAliasesAndLastOccurrenceOffsets() {
        val formatter = SimpleFormatter.compile("{0}-{1}-{0}")
        val result = StringBuilder("old")
        val offsets = IntArray(4) { 99 }
        assertSame(result, formatter.formatAndReplace(result, offsets, result, "new"))
        assertEquals("old-new-old", result.toString())
        assertContentEquals(intArrayOf(8, 4, -1, -1), offsets)
        val append = StringBuilder("prefix")
        assertFailsWith<IllegalArgumentException> { formatter.formatAndAppend(append, offsets, append, "new") }
        assertContentEquals(intArrayOf(-1, -1, -1, -1), offsets)
        assertEquals("prefix", append.toString())
    }
    @Test fun longLiteralsCodePointsAndIterator() {
        val raw = "a".repeat(65278) + "😀" + "{1}" + "tail"
        val compiled = SimpleFormatterImpl.compileToStringMinMaxArguments(raw, StringBuilder(), 0, 2)
        assertEquals(65284, SimpleFormatterImpl.getLength(compiled, false))
        // The surrogate pair straddles literal segments and is counted separately by ICU.
        assertEquals(65284, SimpleFormatterImpl.getLength(compiled, true))
        val output = StringBuilder()
        var state = 0L
        while (true) {
            state = SimpleFormatterImpl.IterInternal.step(state, compiled, output)
            if (state == SimpleFormatterImpl.IterInternal.DONE) break
            output.append(if (SimpleFormatterImpl.IterInternal.getArgIndex(state) == 1) "value" else "bad")
        }
        assertEquals(raw.replace("{1}", "value"), output.toString())
    }
}
