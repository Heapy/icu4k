package io.github.heapy.icu4k.impl.locale

import com.ibm.icu.impl.locale.AsciiUtil as J
import com.ibm.icu.impl.locale.StringTokenIterator as JT
import kotlin.random.Random
import kotlin.test.*

class LocalePrimitivesOracleTest {
    @Test fun asciiOperationsMatchOriginalForEveryUtf16UnitAndRandomStrings() {
        for (code in 0..0xffff) {
            val c = code.toChar()
            assertEquals(J.toUpper(c), AsciiUtil.toUpper(c)); assertEquals(J.toLower(c), AsciiUtil.toLower(c))
            assertEquals(J.isAlpha(c), AsciiUtil.isAlpha(c)); assertEquals(J.isNumeric(c), AsciiUtil.isNumeric(c))
            assertEquals(J.isAlphaNumeric(c), AsciiUtil.isAlphaNumeric(c))
        }
        val random = Random(818051)
        val strings = listOf("", "en-US", "LATN", "1AbC", "_ABC", "éABC", "A", "a", "İıΣσ😀ABC", "\ud800Ab\udc00") +
            List(1000) { buildString { repeat(random.nextInt(0, 30)) { append(random.nextInt(0x10000).toChar()) } } }
        for (s in strings) {
            assertEquals(J.toLowerString(s), AsciiUtil.toLowerString(s)); assertEquals(J.toUpperString(s), AsciiUtil.toUpperString(s))
            assertEquals(J.toTitleString(s), AsciiUtil.toTitleString(s))
            assertEquals(J.isAlphaString(s), AsciiUtil.isAlphaString(s)); assertEquals(J.isNumericString(s), AsciiUtil.isNumericString(s))
            assertEquals(J.isAlphaNumericString(s), AsciiUtil.isAlphaNumericString(s))
            val originalKey = J.CaseInsensitiveKey(s); val key = AsciiUtil.CaseInsensitiveKey(s)
            assertEquals(originalKey.hashCode(), key.hashCode())
            for (other in listOf(s, s.uppercase(), s.lowercase(), s + "x")) {
                assertEquals(J.caseIgnoreMatch(s, other), AsciiUtil.caseIgnoreMatch(s, other))
                assertEquals(J.caseIgnoreCompare(s, other), AsciiUtil.caseIgnoreCompare(s, other))
                assertEquals(originalKey == J.CaseInsensitiveKey(other), key == AsciiUtil.CaseInsensitiveKey(other))
            }
        }
    }
    @Test fun tokenStateOffsetsRestartsAndInvalidOffsetsMatchOriginal() {
        val random = Random(190018)
        val texts = listOf("", "en--US-", "-", "a_b-c__", "😀-a", "\ud800\udc00\ud800") +
            List(150) { buildString { repeat(random.nextInt(30)) { append("abc-_😀".random(random)) } } }
        for (text in texts) for (delimiters in listOf("", "-", "-_", "aa", "\ud800", "😀")) {
            val j = JT(text, delimiters); val k = StringTokenIterator(text, delimiters)
            fun state() {
                assertEquals(j.current(), k.current()); assertEquals(j.currentStart(), k.currentStart())
                assertEquals(j.currentEnd(), k.currentEnd()); assertEquals(j.isDone, k.isDone()); assertEquals(j.hasNext(), k.hasNext())
            }
            state()
            repeat(text.length + 3) { assertEquals(j.next(), k.next()); state() }
            assertEquals(j.first(), k.first()); state()
            for (offset in 0..text.length) { assertSame(j, j.setStart(offset)); assertSame(k, k.setStart(offset)); state() }
            for (offset in listOf(-1, text.length + 1)) {
                assertFailsWith<IndexOutOfBoundsException> { j.setStart(offset) }
                assertFailsWith<IndexOutOfBoundsException> { k.setStart(offset) }
                state()
            }
            assertSame(j, j.setText("-x--")); assertSame(k, k.setText("-x--")); state()
        }
    }
    @Test fun parseStatusAndSyntaxExceptionMetadataMatchOriginal() {
        val j = com.ibm.icu.impl.locale.ParseStatus(); val k = ParseStatus()
        for ((field, value) in mapOf("_parseLength" to 17, "_errorIndex" to 9, "_errorMsg" to "bad subtag")) {
            j.javaClass.getDeclaredField(field).apply { isAccessible = true }.set(j, value)
        }
        k._parseLength = 17; k._errorIndex = 9; k._errorMsg = "bad subtag"
        repeat(2) {
            assertEquals(j.parseLength, k.getParseLength()); assertEquals(j.errorIndex, k.getErrorIndex())
            assertEquals(j.errorMessage, k.getErrorMessage()); assertEquals(j.isError, k.isError())
            j.reset(); k.reset()
        }
        for (index in listOf(-1, 0, 17, Int.MAX_VALUE)) {
            val je = com.ibm.icu.impl.locale.LocaleSyntaxException("bad", index); val ke = LocaleSyntaxException("bad", index)
            assertEquals(je.message, ke.message); assertEquals(je.errorIndex, ke.getErrorIndex())
        }
        assertEquals(com.ibm.icu.impl.locale.LocaleSyntaxException("x").errorIndex, LocaleSyntaxException("x").getErrorIndex())
    }
}
