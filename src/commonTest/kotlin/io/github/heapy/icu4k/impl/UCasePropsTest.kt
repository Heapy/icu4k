package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.lang.UCharacter
import kotlin.test.*

class UCasePropsTest {
    private fun context(before: IntArray, after: IntArray): UCaseProps.ContextIterator = object : UCaseProps.ContextIterator {
        private var values = after
        private var index = 0
        override fun reset(dir: Int) { values = if (dir < 0) before.reversedArray() else after; index = 0 }
        override fun next(): Int = if (index < values.size) values[index++] else -1
    }
    @Test fun appliesContextualSpecialCasing() {
        val props = UCaseProps.INSTANCE
        val output = StringBuilder()
        assertEquals(0x3c2, props.toFullLower(0x3a3, context(intArrayOf(0x39f), intArrayOf()), output, 1))
        assertEquals(0x3c3, props.toFullLower(0x3a3, context(intArrayOf(0x39f), intArrayOf(0x391)), output, 1))
        assertEquals(0x131, props.toFullLower(0x49, context(intArrayOf(), intArrayOf()), output, 2))
        assertEquals(0x69, props.toFullLower(0x49, context(intArrayOf(), intArrayOf(0x307)), output, 2))
        assertEquals(0, props.toFullLower(0x307, context(intArrayOf(0x49), intArrayOf()), output, 2))
        assertEquals(2, props.toFullLower(0x49, context(intArrayOf(), intArrayOf(0x301)), output, 3))
        assertEquals("i\u0307", output.toString())
        output.clear()
        assertEquals(2, props.toFullUpper(0x587, null, output, 6))
        assertEquals("\u0535\u054e", output.toString())
        assertEquals("sss", UCharacter.foldCase("Straße", 0x4000))
    }

    @Test fun mapsSimpleCasesAndFullFolding() {
        val props = UCaseProps.INSTANCE
        assertEquals('a'.code, props.tolower('A'.code))
        assertEquals('A'.code, props.toupper('a'.code))
        assertEquals(0x1c5, props.totitle(0x1c6))
        assertEquals(0x10428, props.tolower(0x10400))
        assertEquals(0x131, props.fold('I'.code, 1))
        assertEquals('i'.code, props.fold('I'.code, 0))
        assertEquals(0x130, props.fold(0x130, 0))
        assertEquals("strasse", UCharacter.foldCase("Straße", true))
        assertEquals("i\u0307", UCharacter.foldCase("İ", true))
        assertEquals("i", UCharacter.foldCase("İ", false))
        assertEquals("\u03c3\u03c3", UCharacter.foldCase("Σς", true))
        assertEquals("ffi", UCharacter.foldCase("\ufb03", true))
        assertTrue(props.isSoftDotted('i'.code))
        assertTrue(props.isCaseSensitive('a'.code))
        assertEquals(-1, props.tolower(-1))
    }
}
