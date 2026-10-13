package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.PatternProps as JavaProps
import kotlin.random.Random
import kotlin.test.*

class PatternPropsOracleTest {
    @Test fun propertiesMatchForEveryUnicodeCodePoint() {
        for (cp in -1..0x110000) {
            assertEquals(JavaProps.isSyntax(cp), PatternProps.isSyntax(cp), "syntax $cp")
            assertEquals(JavaProps.isWhiteSpace(cp), PatternProps.isWhiteSpace(cp), "space $cp")
            assertEquals(JavaProps.isSyntaxOrWhiteSpace(cp), PatternProps.isSyntaxOrWhiteSpace(cp), "union $cp")
        }
        for (cp in listOf(Int.MIN_VALUE, Int.MAX_VALUE)) assertFalse(PatternProps.isSyntaxOrWhiteSpace(cp))
    }
    @Test fun stringOperationsMatchIncludingSpaceCharTrailingQuirk() {
        val random = Random(410)
        val alphabet = charArrayOf('a', '!', ' ', '\t', '\u0085', '\u00a0', '\u1680', '\u200e', '\u2028', '\u202f', '\u3000', 0xd800.toChar(), 0xffff.toChar())
        repeat(2000) {
            val text = CharArray(random.nextInt(30)) { alphabet.random(random) }.concatToString()
            assertEquals(JavaProps.trimWhiteSpace(text), PatternProps.trimWhiteSpace(text))
            val expectedTrim = runCatching { JavaProps.trimSpaceChar(text) }
            val actualTrim = runCatching { PatternProps.trimSpaceChar(text) }
            if (expectedTrim.isSuccess) assertEquals(expectedTrim.getOrThrow(), actualTrim.getOrThrow())
            else assertTrue(actualTrim.exceptionOrNull() is IndexOutOfBoundsException)
            assertEquals(JavaProps.isIdentifier(text), PatternProps.isIdentifier(text))
            for (start in 0..text.length) {
                assertEquals(JavaProps.skipWhiteSpace(text, start), PatternProps.skipWhiteSpace(text, start))
                assertEquals(JavaProps.skipIdentifier(text, start), PatternProps.skipIdentifier(text, start))
                for (end in start..text.length) assertEquals(JavaProps.isIdentifier(text, start, end), PatternProps.isIdentifier(text, start, end))
            }
        }
    }
}
