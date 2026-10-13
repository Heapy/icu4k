package io.github.heapy.icu4k.text

import com.ibm.icu.text.UnicodeSet as JavaSet
import io.github.heapy.icu4k.impl.RuleCharacterIterator
import io.github.heapy.icu4k.impl.Utility
import kotlin.test.*

class UnicodeSetPropertyPatternOracleTest {
    @Test fun parseResultsOffsetsAndLookaheadMatchOriginal() {
        val parse = JavaSet::class.java.getDeclaredMethod("applyPropertyPattern", String::class.java, java.text.ParsePosition::class.java, com.ibm.icu.text.SymbolTable::class.java).apply { isAccessible = true }
        val resembles = JavaSet::class.java.getDeclaredMethod("resemblesPropertyPattern", String::class.java, Int::class.javaPrimitiveType).apply { isAccessible = true }
        val patterns = listOf("[:Lu:]", "[: ^ Lu :]", "\\p{gc=Lu}", "\\P{Alphabetic}", "\\p {scx=Latn}", "\\N{LATIN CAPITAL LETTER A}", "\\p{nv=0x1p-1}", "\\p{RGI_Emoji}", "\\P{RGI_Emoji}", "\\p{}", "\\p{", "\\p Lu", "[ :Lu:]", "[:Lu]", "\\n{A}", "\\p{bad}", "[:^:]", "\\N{A=B}")
        for (pattern in patterns) for (prefix in listOf("", "xx")) {
            val text = prefix + pattern + "tail"
            val jp = java.text.ParsePosition(prefix.length); val kp = ParsePosition(prefix.length)
            val js = JavaSet().add("old"); val ks = UnicodeSet().add("old")
            val j = runCatching { parse.invoke(js, text, jp, null) }
            val k = runCatching { ks.applyPropertyPattern(text, kp, null) }
            assertEquals(j.isSuccess, k.isSuccess, text)
            assertEquals(jp.index, kp.index, text)
            if (j.isSuccess) {
                assertEquals(j.getOrNull() == null, k.getOrNull() == null)
                assertEquals(js.toPattern(true), ks.toPattern(true), text)
            }
            for (pos in -1..text.length) assertEquals(resembles.invoke(null, text, pos), UnicodeSet.resemblesPropertyPattern(text, pos), "$text $pos")
        }
        val jp = java.text.ParsePosition(0); val kp = ParsePosition(0)
        val jchars = com.ibm.icu.impl.RuleCharacterIterator("  \\p{Lu}", null, jp)
        val kchars = RuleCharacterIterator("  \\p{Lu}", null, kp)
        val look = JavaSet::class.java.getDeclaredMethod("resemblesPropertyPattern", com.ibm.icu.impl.RuleCharacterIterator::class.java, Int::class.javaPrimitiveType).apply { isAccessible = true }
        for (opts in 0..7) { assertEquals(look.invoke(null, jchars, opts), UnicodeSet.resemblesPropertyPattern(kchars, opts)); assertEquals(jp.index, kp.index) }
        for (s in listOf("A\\B", "\u0000\u007f\u0080", "😀", CharArray(1) { 0xd800.toChar() }.concatToString())) assertEquals(com.ibm.icu.impl.Utility.escape(s), Utility.escape(s))
    }
}
