package io.github.heapy.icu4k.text

import com.ibm.icu.text.RuleBasedBreakIterator as J
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.RBBIDataWrapper
import io.github.heapy.icu4k.util.BinaryBuffer
import java.io.ByteArrayOutputStream
import kotlin.random.Random
import kotlin.test.*

class RuleCompilationOracleTest {
    private fun compile(rules: String): ByteArray {
        val output = ByteArrayOutputStream()
        val expected = runCatching { J.compileRules(rules, output); output.toByteArray() }
        val actual = runCatching { RuleBasedBreakIterator.compileRules(rules) }
        if (expected.isFailure) {
            val je = expected.exceptionOrNull()!!
            val ke = actual.exceptionOrNull() ?: fail("Accepted invalid rules: $rules")
            assertEquals(je.javaClass, ke.javaClass, rules); assertEquals(je.message, ke.message, rules)
            return ByteArray(0)
        }
        val bytes = actual.getOrElse { throw AssertionError("Failed to compile $rules", it) }
        assertContentEquals(expected.getOrThrow(), bytes, rules)
        val parsed = RBBIDataWrapper.get(BinaryBuffer(bytes))
        assertEquals(J(rules).toString(), parsed.fRuleSource)
        return bytes
    }
    private fun iterate(rules: String, text: String) {
        val j = J(rules)
        val k = RuleBasedBreakIterator(rules)
        assertEquals(j.toString(), k.toString()); assertEquals(j.hashCode(), k.hashCode())
        j.setText(text); k.setText(text)
        fun state() {
            assertEquals(j.current(), k.current(), "$rules: $text")
            assertEquals(j.ruleStatus, k.getRuleStatus())
            val ja = IntArray(j.getRuleStatusVec(null)); val ka = IntArray(k.getRuleStatusVec(null))
            assertEquals(j.getRuleStatusVec(ja), k.getRuleStatusVec(ka)); assertContentEquals(ja, ka)
        }
        assertEquals(j.first(), k.first()); state()
        repeat(text.length + 2) { assertEquals(j.next(), k.next()); state() }
        assertEquals(j.last(), k.last()); state()
        repeat(text.length + 2) { assertEquals(j.previous(), k.previous()); state() }
        for (offset in 0..text.length) {
            assertEquals(j.following(offset), k.following(offset)); state()
            assertEquals(j.preceding(offset), k.preceding(offset)); state()
            assertEquals(j.isBoundary(offset), k.isBoundary(offset)); state()
        }
        val copy = k.clone(); assertEquals(k, copy)
        assertEquals(k.next(), copy.next())
    }
    @Test fun completeBinaryCompilationMatchesIcuForOriginalRuleSources() {
        for (name in listOf("char", "word", "word_POSIX", "line", "line_cj", "line_loose", "line_loose_cj", "line_normal",
            "line_normal_cj", "line_phrase_cj", "line_loose_phrase_cj", "line_normal_phrase_cj", "sent", "sent_el", "title")) {
            val rules = RBBIDataWrapper.get(BinaryBuffer(ICUData.getBytes("brkitr/$name.brk")!!)).fRuleSource
            assertTrue(compile(rules).isNotEmpty(), name)
            iterate(rules, "Aé e\u0301. One two!\r\n😀日本語ไทยabc")
        }
    }
    @Test fun customRulesBinaryErrorsAndPublicIterationMatchOriginal() {
        val rules = listOf("a;.;", "!!chain;ab{1};bc{2};.;", "[a{bof}]b{5};.;", "a/b{10};ab{20};.;",
            "!!lookAheadHardBreak;a/b{10};.;", "a{3};a{1};a{0};.;", "\$dictionary=[a-z];\$dictionary+{100};.;",
            "'a b'{4};.; # trailing comment", "[😀-🙏]+{7};.;", "\\U0001f600;.;", "[a{eof}];.;",
            "\\" + "\ud800" + ";.;", "\\" + "\udc00" + ";.;", "\\uD800;.;")
        for (rule in rules) {
            assertTrue(compile(rule).isNotEmpty(), rule)
            for (text in listOf("", "abcbc a b aa", "😀😁🙏x", "a\ud800b\udc00c", "ab".repeat(80))) iterate(rule, text)
        }
        for (rule in listOf("", "a", "[];", "\$x;", "a{2147483648};", "!!unknown;a;", "'a\nb';", "\ud800;", "[a-;")) {
            assertTrue(compile(rule).isEmpty(), rule)
            val j = assertFailsWith<IllegalArgumentException> { J(rule) }
            val k = assertFailsWith<IllegalArgumentException> { RuleBasedBreakIterator(rule) }
            assertEquals(j.message, k.message)
        }
        val random = Random(88345)
        repeat(60) {
            val rule = buildString {
                if (random.nextBoolean()) append("!!chain;")
                repeat(random.nextInt(1, 7)) {
                    repeat(random.nextInt(1, 4)) {
                        append(listOf("a", "[a-c]", "[\\p{N}]", "(a|b)").random(random))
                        append(listOf("", "*", "+", "?").random(random))
                    }
                    append("{${random.nextInt(100)}};")
                }
                append(".;")
            }
            compile(rule)
            iterate(rule, buildString { repeat(30) { append("abcd012 ".random(random)) } })
        }
    }
}
