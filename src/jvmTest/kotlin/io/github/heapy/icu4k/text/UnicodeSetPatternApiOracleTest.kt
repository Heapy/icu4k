package io.github.heapy.icu4k.text

import com.ibm.icu.text.UnicodeSet as J
import kotlin.test.*

class UnicodeSetPatternApiOracleTest {
    private fun compare(j: J, k: UnicodeSet) {
        assertEquals(j.toPattern(false), k.toPattern(false))
        assertEquals(j.toPattern(true), k.toPattern(true))
        assertEquals(j.toString(), k.toString())
        assertEquals(j.rangeCount, k.getRangeCount())
        for (i in 0 until j.rangeCount) { assertEquals(j.getRangeStart(i), k.getRangeStart(i)); assertEquals(j.getRangeEnd(i), k.getRangeEnd(i)) }
        assertEquals(j.strings().toList(), k.strings().toList())
    }
    @Test fun constructorsOverloadsParsePositionsAndFailedParseCacheMatchOriginal() {
        val patterns = listOf("[]", " [ a - z ] ", "[[a-c]{ab}]", "[[:Lu:]&[A-Z]]", "[^a{ab}]", "[{ab}-{cd}]",
            "[k]", "[a$]", "[[\\n\\\\]{é😀}]", "[[:ASCII:]\\u0001]", "[a] tail", "[a-a]", "[", "\\p{Greek}")
        for (pattern in patterns) for (option in 0..7) {
            val j = J().add("seed"); val k = UnicodeSet().add("seed")
            val je = runCatching { j.applyPattern(pattern, option) }.exceptionOrNull()
            val ke = runCatching { k.applyPattern(pattern, option) }.exceptionOrNull()
            assertEquals(je?.message, ke?.message, "$pattern option=$option")
            compare(j, k)
            if (je == null) {
                compare(J(pattern, option), UnicodeSet(pattern, option))
                if (option <= 1) compare(J(pattern, option == 1), UnicodeSet(pattern, option == 1))
                if (option == 1) compare(J(pattern), UnicodeSet(pattern))
            }
            val input = "xx" + pattern + "rest"
            val jp = java.text.ParsePosition(2); val kp = ParsePosition(2)
            val a = J(); val b = UnicodeSet()
            val ae = runCatching { a.applyPattern(input, jp, null, option) }.exceptionOrNull()
            val be = runCatching { b.applyPattern(input, kp, null, option) }.exceptionOrNull()
            assertEquals(ae?.message, be?.message)
            assertEquals(jp.index, kp.index); assertEquals(jp.errorIndex, kp.errorIndex)
            compare(a, b)
            if (ae == null) {
                compare(J(input, java.text.ParsePosition(2), null, option), UnicodeSet(input, ParsePosition(2), null, option))
                if (option == 1) compare(J(input, java.text.ParsePosition(2), null), UnicodeSet(input, ParsePosition(2), null))
            }
        }
    }
    @Test fun everyMutationPreservesOrInvalidatesPatternAsOriginal() {
        for (action in 0..24) {
            val j = J("[[a-c]{ab}]"); val k = UnicodeSet("[[a-c]{ab}]")
            when (action) {
                0 -> { j.add(98); k.add(98) }
                1 -> { j.add(100); k.add(100) }
                2 -> { j.add(98, 99); k.add(98, 99) }
                3 -> { j.add(110, 120); k.add(110, 120) }
                4 -> { j.add(120, 110); k.add(120, 110) }
                5 -> { j.add("ab"); k.add("ab") }
                6 -> { j.add("cd"); k.add("cd") }
                7 -> { j.remove(120); k.remove(120) }
                8 -> { j.remove(120, 110); k.remove(120, 110) }
                9 -> { j.remove("missing"); k.remove("missing") }
                10 -> { j.remove("ab"); k.remove("ab") }
                11 -> { j.retain(97, 99); k.retain(97, 99) }
                12 -> { j.retain("ab"); k.retain("ab") }
                13 -> { j.complement(120, 110); k.complement(120, 110) }
                14 -> { j.complement(); k.complement() }
                15 -> { j.complement("ab"); k.complement("ab") }
                16 -> { j.removeAllStrings(); k.removeAllStrings() }
                17 -> { j.addAll(J()); k.addAll(UnicodeSet()) }
                18 -> { j.removeAll(J()); k.removeAll(UnicodeSet()) }
                19 -> { j.retainAll(J(j)); k.retainAll(UnicodeSet(k)) }
                20 -> { j.complementAll(J()); k.complementAll(UnicodeSet()) }
                21 -> { j.compact(); k.compact() }
                22 -> { j.closeOver(0); k.closeOver(0) }
                23 -> { j.clear(); k.clear() }
                24 -> { j.set(J("[[x-z]]")); k.set(UnicodeSet("[[x-z]]")) }
            }
            compare(j, k)
            compare(J(j), UnicodeSet(k)); compare(j.cloneAsThawed(), k.cloneAsThawed())
            compare(j.freeze(), k.freeze())
        }
        val j = J("[[:ASCII:]]"); val k = UnicodeSet("[[:ASCII:]]")
        j.removeAllStrings(); k.removeAllStrings(); compare(j, k)
        val a = J("[{ab}]"); val b = UnicodeSet("[{ab}]")
        a.retain("ab"); b.retain("ab"); compare(a, b)
    }
    @Test fun publicSymbolsRetainStandinPatternsAndRejectVariableRemainders() {
        for (value in listOf("[a-c]", "[a-c]tail", "[[:Greek:]]", "\ue000")) {
            val js = object : com.ibm.icu.text.SymbolTable {
                override fun lookup(s: String): CharArray? = if (s == "v") value.toCharArray() else null
                override fun lookupMatcher(c: Int): com.ibm.icu.text.UnicodeMatcher? = if (c == 0xe000) J("[[a-c]{ab}]").freeze() else null
                override fun parseReference(text: String, pos: java.text.ParsePosition, limit: Int): String? {
                    if (pos.index < limit && text[pos.index] == 'v') { pos.index++; return "v" }
                    return null
                }
            }
            val ks = object : SymbolTable {
                override fun lookup(s: String): CharArray? = if (s == "v") value.toCharArray() else null
                override fun lookupMatcher(c: Int): UnicodeMatcher? = if (c == 0xe000) UnicodeSet("[[a-c]{ab}]").freeze() else null
                override fun parseReference(text: String, pos: ParsePosition, limit: Int): String? {
                    if (pos.index < limit && text[pos.index] == 'v') { pos.index++; return "v" }
                    return null
                }
            }
            for (pattern in listOf("\$v", "[\$v]", "[\$v-[b]]")) {
                val j = J(); val k = UnicodeSet()
                val jp = java.text.ParsePosition(0); val kp = ParsePosition(0)
                val je = runCatching { j.applyPattern(pattern, jp, js, 1) }.exceptionOrNull()
                val ke = runCatching { k.applyPattern(pattern, kp, ks, 1) }.exceptionOrNull()
                assertEquals(je?.message, ke?.message)
                assertEquals(jp.index, kp.index)
                compare(j, k)
            }
        }
    }

}
