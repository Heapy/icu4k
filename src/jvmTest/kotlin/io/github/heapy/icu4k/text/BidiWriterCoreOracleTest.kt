// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import com.ibm.icu.text.Bidi as Source
import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

class BidiWriterCoreOracleTest {
    private fun outcome(block: () -> String): Any = try { block() } catch (e: IndexOutOfBoundsException) { "<bounds>" }
    private fun field(name: String) = Source::class.java.getDeclaredField(name).apply { isAccessible = true }
    private val textField = field("text")
    private val lengthField = field("length")
    private val modeField = field("reorderingMode")
    private val optionsField = field("reorderingOptions")
    private val propsField = field("dirProps")
    private val runsField = field("runs")
    private val insertField = com.ibm.icu.text.BidiRun::class.java.getDeclaredField("insertRemove").apply { isAccessible = true }
    private val flagAt = Source::class.java.getDeclaredMethod("testDirPropFlagAt", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply { isAccessible = true }
    private fun input(source: Source) = object : BidiWriterInput {
        override val text get() = textField.get(source) as CharArray
        override val length get() = lengthField.getInt(source)
        override val reorderingMode get() = modeField.getInt(source)
        override val reorderingOptions get() = optionsField.getInt(source)
        override val dirProps get() = propsField.get(source) as ByteArray?
        override fun countRuns(): Int = source.countRuns()
        override fun getVisualRun(index: Int): BidiRun = source.getVisualRun(index).let { BidiRun(it.start, it.limit, it.embeddingLevel) }
        override fun getInsertRemove(index: Int): Int = insertField.getInt((runsField.get(source) as Array<*>)[index])
        override fun isInverse(): Boolean = source.isInverse()
        override fun testDirPropFlagAt(flag: Int, index: Int): Boolean = try { flagAt.invoke(source, flag, index) as Boolean }
            catch (e: InvocationTargetException) { throw e.cause!! }
    }
    @Test fun visualWriterFromResolvedSourceParagraphsEveryModeAndOption() {
        val texts = listOf("", "abc", "אבג", "a(אב) 12 ا٣\u200f Z\u200d", "\u2067אב [1]\u2069 Á (𝄞) 𞸀\u2029abc",
            "A\u202e(אב) 123\u202cZ", "1\u200e2\u200f3\u200c4\u200d5", "abc\r\nאבג\nا1", "(á) אּ😀 [بّ]", "\ud800אב\udc00A")
        val resolverFailures = sortedSetOf<String>()
        for (mode in 0..6) for (reordering in listOf(0, 1, 2, 3, 4)) for (level in listOf<Byte>(0, 1, 126, 127)) for ((textIndex, text) in texts.withIndex()) {
            val source = Source().apply { setReorderingMode(mode); setReorderingOptions(reordering) }
            try { source.setPara(text, level, null) } catch (e: ArrayIndexOutOfBoundsException) {
                assertEquals(4, reordering)
                resolverFailures.add("$mode/$reordering/$level/$textIndex ${e.javaClass.simpleName}")
                continue
            }
            val input = input(source)
            for (options in (0..31).toList() + listOf(Int.MIN_VALUE, Int.MAX_VALUE)) {
                assertEquals(outcome { source.writeReordered(options) }, outcome { BidiWriterCore.writeReordered(input, options) }, "$mode/$reordering/$level/$options/$text")
            }
        }
        val expected = javaClass.getResourceAsStream("/bidi-source-resolver-failures.txt")!!.bufferedReader().use { it.readLines() }.toSortedSet()
        assertEquals(expected, resolverFailures, "pinned source resolver failures are not writer inputs")
    }
    @Test fun randomizedParagraphWriterAndReversal() {
        val random = Random(78_308)
        val alphabet = "abc XYZאבגדابتث012٣٤()[]{}<>= \u0301\u05b0\u064b\u200c\u200d\u200e\u200f\u202a\u202b\u202c\u202d\u202e\u2066\u2067\u2068\u2069\n\r\ud83b\ude00\ud834\udd1e"
        val resolverFailures = sortedSetOf<String>()
        repeat(10_000) { i ->
            val text = CharArray(random.nextInt(1, 80)) { alphabet.random(random) }.concatToString()
            val mode = random.nextInt(7)
            val reorder = listOf(0, 1, 2, 4).random(random)
            val level = listOf<Byte>(0, 1, 126, 127).random(random)
            val source = Source().apply { setReorderingMode(mode); setReorderingOptions(reorder) }
            val resolved = try { source.setPara(text, level, null); true } catch (e: Exception) {
                assertTrue(e is ArrayIndexOutOfBoundsException || e is NullPointerException, "source resolver iteration $i/$mode/$reorder/$level: $e")
                e.stackTrace.firstOrNull()?.let { assertEquals("com.ibm.icu.text.Bidi", it.className) }
                val hex = text.map { it.code.toString(16).padStart(4, '0') }.joinToString("")
                resolverFailures.add("$mode/$reorder/$level/$hex ${e.javaClass.simpleName}")
                false
            }
            val options = random.nextInt(32)
            if (resolved) assertEquals(outcome { source.writeReordered(options) }, outcome { BidiWriterCore.writeReordered(input(source), options) }, "iteration $i/$mode/$reorder/$level/$options")
            for (reverseOption in 0..7) {
                val flags = (reverseOption and 3) or ((reverseOption and 4) shl 1)
                assertEquals(Source.writeReverse(text, flags), BidiWriterCore.reverse(text, flags), "reverse $i/$flags")
            }
        }
        val expected = javaClass.getResourceAsStream("/bidi-source-random-resolver-failures.txt")!!.bufferedReader().use { it.readLines() }.toSortedSet()
        assertEquals(expected, resolverFailures)
    }
    @Test fun reverseAllBmpCombiningMirrorsControlsAndSurrogateCases() {
        for (c in 0..0xffff) {
            val text = "(" + c.toChar() + "\u0301)"
            assertEquals(Source.writeReverse(text, 11), BidiWriterCore.reverse(text, 11), "bmp ${c.toString(16)}")
        }
        for (c in 0x10000..0x10ffff) {
            val type = com.ibm.icu.lang.UCharacter.getType(c)
            if (type in 6..8 || com.ibm.icu.lang.UCharacter.isMirrored(c)) {
                val text = "A(" + String(Character.toChars(c)) + ")\u0301B"
                for (options in 0..31) assertEquals(Source.writeReverse(text, options), BidiWriterCore.reverse(text, options), "supplementary ${c.toString(16)}/$options")
            }
        }
        for (text in listOf("", "\ud800", "\udc00", "\ud800\udc00\ud800", "\u0301\u0302abc", "\u200d\u0301A"))
            for (options in (0..31).toList() + listOf(Int.MIN_VALUE, Int.MAX_VALUE)) assertEquals(Source.writeReverse(text, options), BidiWriterCore.reverse(text, options))
        assertFailsWith<IllegalArgumentException> { Source.writeReverse(null, 0) }
        assertFailsWith<IllegalArgumentException> { BidiWriterCore.reverse(null, 0) }
    }
    @Test fun writerPublicRunAndInverseDispatchSequenceMatchesSource() {
        val trace = ArrayList<String>()
        val source = object : Source() {
            override fun countRuns(): Int { trace.add("count"); return super.countRuns() }
            override fun getVisualRun(index: Int): com.ibm.icu.text.BidiRun { trace.add("run:$index"); return super.getVisualRun(index) }
            override fun isInverse(): Boolean { trace.add("inverse"); return super.isInverse() }
        }
        source.setReorderingMode(5)
        source.setReorderingOptions(1)
        source.setPara("A אב(12) ا3", 1, null)
        for (options in 0..31) {
            trace.clear()
            val expected = source.writeReordered(options)
            val expectedTrace = trace.toList()
            trace.clear()
            assertEquals(expected, BidiWriterCore.writeReordered(input(source), options))
            assertEquals(expectedTrace, trace, "options $options")
        }
    }
}
