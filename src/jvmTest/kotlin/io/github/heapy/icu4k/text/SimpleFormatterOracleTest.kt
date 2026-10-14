package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.SimpleFormatterImpl
import java.io.IOException
import kotlin.random.Random
import kotlin.test.*
import com.ibm.icu.impl.SimpleFormatterImpl as Original

class SimpleFormatterOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (failure: Throwable) { listOf(failure.javaClass.simpleName, failure.message) }
    @Test fun syntaxCompiledTapeAndDiagnosticsMatchGeneratedCorpus() {
        val patterns = mutableListOf("", "{0} {1}", "{0} ({1})", "{0}, {1}", "{0} – {1}", "{255}", "{256}", "{01}", "{00}", "{999999}", "{0", "}", "'{0}", "''{0}''", "{9}", "{1}{0}{1}", "a".repeat(65279), "a".repeat(65280), "a".repeat(130558), "a".repeat(65278) + "😀{0}")
        val random = Random(9871)
        val pieces = listOf("a", "😀", "{0}", "{1}", "{3}", "'", "''", "'{", "'}", "{", "}", "{10}", "{01}", "{256}", " ")
        repeat(2000) { patterns.add(buildString { repeat(random.nextInt(1, 12)) { append(pieces[random.nextInt(pieces.size)]) } }) }
        for (pattern in patterns) for ((min, max) in listOf(0 to 256, 0 to 2, 2 to 2, 3 to 1)) {
            val j = StringBuilder("seed")
            val k = StringBuilder("seed")
            val expected = outcome { Original.compileToStringMinMaxArguments(pattern, j, min, max) }
            val actual = outcome { SimpleFormatterImpl.compileToStringMinMaxArguments(pattern, k, min, max) }
            assertEquals(expected, actual, "$pattern/$min/$max tape")
            assertEquals(j.toString(), k.toString(), "$pattern builder")
            if (expected is String) {
                assertEquals(outcome { Original.getTextWithNoArguments(expected) }, outcome { SimpleFormatterImpl.getTextWithNoArguments(expected) })
                assertEquals(Original.getLength(expected, false), SimpleFormatterImpl.getLength(expected, false))
                assertEquals(Original.getLength(expected, true), SimpleFormatterImpl.getLength(expected, true))
                assertEquals(Original.getPrefixLength(expected), SimpleFormatterImpl.getPrefixLength(expected))
                val values = Array<CharSequence?>(Original.getArgumentLimit(expected)) { if (it % 3 == 0) null else "value$it" }
                assertEquals(Original.formatCompiledPattern(expected, *values), SimpleFormatterImpl.formatCompiledPattern(expected, *values))
                assertEquals(com.ibm.icu.text.SimpleFormatter.compile(pattern).toString(), SimpleFormatter.compile(pattern).toString())
            }
        }
    }
    @Test fun appendReplaceAliasesPartialWritesAndOffsetsMatch() {
        for (pattern in listOf("", "literal", "{0}", "{1}{0}", "{0}-{1}-{0}", "prefix{1}{0}tail", "{2}{0}{1}{2}"))
            for (replace in listOf(false, true)) for (alias in -1..2) for (count in 0..4) for (offsetCount in 0..5) {
                val compiled = Original.compileToStringMinMaxArguments(pattern, StringBuilder(), 0, 3)
                val j = StringBuilder("seed")
                val k = StringBuilder("seed")
                val jo = IntArray(offsetCount) { 77 }
                val ko = jo.copyOf()
                val jv = Array<CharSequence?>(count) { if (it == alias) j else if (it == 1) null else "v$it" }
                val kv = Array<CharSequence?>(count) { if (it == alias) k else if (it == 1) null else "v$it" }
                val expected = outcome { if (replace) Original.formatAndReplace(compiled, j, jo, *jv).toString() else Original.formatAndAppend(compiled, j, jo, *jv).toString() }
                val actual = outcome { if (replace) SimpleFormatterImpl.formatAndReplaceValues(compiled, k, ko, kv).toString() else SimpleFormatterImpl.formatAndAppendValues(compiled, k, ko, kv).toString() }
                assertEquals(expected, actual, "$pattern/$replace/$alias/$count/$offsetCount")
                assertEquals(j.toString(), k.toString())
                assertContentEquals(jo, ko)
            }
    }
    @Test fun wrapperKeepsJavaArgumentArrayLiveDuringCallbacks() {
        fun arguments(): Array<CharSequence?> {
            val values = arrayOfNulls<CharSequence>(2)
            values[0] = object : CharSequence {
                override val length: Int get() = 1
                override fun get(index: Int): Char { values[1] = "changed"; return 'a' }
                override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = "a".subSequence(startIndex, endIndex)
            }
            values[1] = "original"
            return values
        }
        val arrayClass = arrayOfNulls<CharSequence>(0).javaClass
        val source = com.ibm.icu.text.SimpleFormatter.compile("{0}{1}")
        val common = SimpleFormatter.compile("{0}{1}")
        val expected = source.javaClass.getMethod("format", arrayClass).invoke(source, arguments() as Any)
        val actual = common.javaClass.getMethod("format", arrayClass).invoke(common, arguments() as Any)
        assertEquals("achanged", expected)
        assertEquals(expected, actual)
    }
    @Test fun iteratorAndIoFailuresMatchSource() {
        val compiled = Original.compileToStringMinMaxArguments("pre{1}middle{0}end", StringBuilder(), 0, 2)
        var js = 0L
        var ks = 0L
        val j = StringBuilder()
        val k = StringBuilder()
        while (true) {
            js = Original.IterInternal.step(js, compiled, j)
            ks = SimpleFormatterImpl.IterInternal.step(ks, compiled, k)
            assertEquals(js, ks)
            assertEquals(j.toString(), k.toString())
            if (js == -1L) break
            assertEquals(Original.IterInternal.getArgIndex(js), SimpleFormatterImpl.IterInternal.getArgIndex(ks))
            j.append("value"); k.append("value")
        }
        val output = object : Appendable {
            override fun append(csq: CharSequence?): Appendable = throw IOException("fixture")
            override fun append(csq: CharSequence?, start: Int, end: Int): Appendable = throw IOException("fixture")
            override fun append(c: Char): Appendable = throw IOException("fixture")
        }
        assertEquals(outcome { Original.IterInternal.step(0, compiled, output) }, outcome { SimpleFormatterImpl.IterInternal.step(0, compiled, output) })
        assertEquals(Original.compileToStringMinMaxArguments("{0} {1}", null, 0, 2), SimpleFormatterImpl.compileToStringMinMaxArguments("{0} {1}", null, 0, 2))
    }
}
