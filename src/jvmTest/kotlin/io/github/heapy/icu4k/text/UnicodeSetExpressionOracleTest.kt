package io.github.heapy.icu4k.text

import com.ibm.icu.text.UnicodeSet as J
import com.ibm.icu.impl.RuleCharacterIterator as JIterator
import io.github.heapy.icu4k.impl.RuleCharacterIterator
import kotlin.random.Random
import kotlin.test.*

class UnicodeSetExpressionOracleTest {
    private val method = J::class.java.getDeclaredMethod("applyPattern", JIterator::class.java,
        com.ibm.icu.text.SymbolTable::class.java, Appendable::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).also { it.isAccessible = true }
    private val variables = mapOf("v" to "a-c", "s" to "[a-z]", "empty" to "", "stand" to "\ue000")
    private val jSymbols = object : com.ibm.icu.text.SymbolTable {
        override fun lookup(s: String): CharArray? = variables[s]?.toCharArray()
        override fun lookupMatcher(c: Int): com.ibm.icu.text.UnicodeMatcher? = if (c == 0xe000) J("[0-9{ab}]").freeze() else null
        override fun parseReference(text: String, pos: java.text.ParsePosition, limit: Int): String? {
            val start = pos.index; var end = start
            while (end < limit && text[end] in 'a'..'z') end++
            if (end == start) return null
            pos.index = end; return text.substring(start, end)
        }
    }
    private val kSymbols = object : SymbolTable {
        override fun lookup(s: String): CharArray? = variables[s]?.toCharArray()
        override fun lookupMatcher(c: Int): UnicodeMatcher? = if (c == 0xe000) UnicodeSet().add('0'.code, '9'.code).add("ab").freeze() else null
        override fun parseReference(text: String, pos: ParsePosition, limit: Int): String? {
            val start = pos.index; var end = start
            while (end < limit && text[end] in 'a'..'z') end++
            if (end == start) return null
            pos.index = end; return text.substring(start, end)
        }
    }
    private fun compare(pattern: String, options: Int, symbols: Boolean = false, prefix: Int = 0) {
        val input = "X".repeat(prefix) + pattern
        val jp = java.text.ParsePosition(prefix); val kp = ParsePosition(prefix)
        val ji = JIterator(input, if (symbols) jSymbols else null, jp)
        val ki = RuleCharacterIterator(input, if (symbols) kSymbols else null, kp)
        val j = J().add("seed"); val k = UnicodeSet().add("seed")
        val jb = StringBuilder(); val kb = StringBuilder()
        val expected = runCatching {
            try { method.invoke(j, ji, if (symbols) jSymbols else null, jb, options, 0) }
            catch (e: java.lang.reflect.InvocationTargetException) { throw e.cause!! }
        }
        val actual = runCatching { k.applyPattern(ki, if (symbols) kSymbols else null, kb, options, 0) }
        val context = "$pattern options=$options symbols=$symbols"
        if (expected.isSuccess) actual.getOrElse { throw AssertionError(context, it) }
        else {
            assertTrue(actual.isFailure, context)
            assertEquals(expected.exceptionOrNull()?.message, actual.exceptionOrNull()?.message, context)
        }
        assertEquals(jp.index, kp.index, context)
        assertEquals(ji.inVariable(), ki.inVariable(), context)
        assertEquals(jb.toString(), kb.toString(), context)
        assertEquals(j.rangeCount, k.getRangeCount(), context)
        for (i in 0 until j.rangeCount) {
            assertEquals(j.getRangeStart(i), k.getRangeStart(i), context); assertEquals(j.getRangeEnd(i), k.getRangeEnd(i), context)
        }
        assertEquals(j.strings().toList(), k.strings().toList(), context)
    }
    @Test fun recursiveSetsPropertiesStringsAnchorsAndErrorsMatchOriginal() {
        val patterns = listOf("[]", "[abc]", "[a-z]", "[^a-c{ab}]", "[-a]", "[a-]", "[a-a]", "[z-a]", "[a&&b]", "[a&[b]]",
            "[[a-z]&[^aeiou]]", "[[a-z]-[m-p]]", "[a[bc]d]", "[:Lu:]", "\\p{sc=Greek}", "[\\p{L}&&\\P{ASCII}]",
            "[{ab}-{cd}]", "[{a}-{c}]", "[{ab}-{a}]", "[{}]", "[{a b}\\ ]", "[a$]", "[\$x]", "[a-$]", "[\\$\\-\\&]",
            "[😀-😁{😀ab}]", "[\\uD800\\uDC00]", "[a] tail", " [ a - c ] ", "[", "[a", "[a-", "[{abc]", "[[]]", "[^]", "[^^]", "[[]&]", "[\\q]", "\\p{badproperty}")
        for (pattern in patterns) for (options in listOf(0, 1, 2, 3, 4, 5, 6, 7)) compare(pattern, options)
        for (pattern in listOf("[\$v]", "\$s", "[\$s-[b]]", "[\$empty a]", "[\$stand]", "\$stand", "[\$missing]", "[\$v$]")) {
            for (option in listOf(0, 1, 2, 4, 6)) compare(pattern, option, true)
        }
        val random = Random(614920)
        val atoms = listOf("a", "z", "a-c", "\\u0041", "\\-", "{ab}", "{cd}", "[a-c]", "[^b]", "[:Lu:]", "\\p{ASCII}", "-", "&", "$")
        repeat(500) {
            val pattern = "[" + List(random.nextInt(1, 7)) { atoms.random(random) }.joinToString("") + "]"
            compare(pattern, random.nextInt(8), prefix = random.nextInt(3))
        }
        for (depth in listOf(99, 100, 101, 102)) compare("[".repeat(depth) + "a" + "]".repeat(depth), 0)
    }
}
