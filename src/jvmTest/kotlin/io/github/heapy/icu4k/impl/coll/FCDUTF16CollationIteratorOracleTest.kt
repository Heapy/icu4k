// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import kotlin.random.Random
import kotlin.test.*
import com.ibm.icu.impl.coll.FCDUTF16CollationIterator as Source
import com.ibm.icu.impl.coll.UTF16CollationIterator as SourceUTF16
import com.ibm.icu.impl.coll.CollationIterator as SourceBase
import com.ibm.icu.impl.coll.CollationData as SourceData
import com.ibm.icu.impl.coll.UVector32 as SourceOffsets

class FCDUTF16CollationIteratorOracleTest {
    private val bridge = CollationDataOracleTest()
    private val fields = listOf("rawSeq", "segmentStart", "segmentLimit", "rawLimit", "normalized", "checkDir")
    private val parentFields = listOf("seq", "start", "pos", "limit")
    @Test fun realRootAndTailoringCEsOffsetsAndBufferState() {
        val field = com.ibm.icu.text.RuleBasedCollator::class.java.getDeclaredField("data").also { it.isAccessible = true }
        val data = listOf(com.ibm.icu.impl.coll.CollationRoot.getData(), field.get(com.ibm.icu.text.Collator.getInstance(com.ibm.icu.util.ULocale("es@collation=traditional"))) as SourceData)
        for (sd in data) {
            val td = bridge.importSource(sd)
            for (numeric in listOf(false, true)) for (text in texts()) {
                val s = Source(sd, numeric, text, 0); val t = FCDUTF16CollationIterator(td, numeric, text, 0)
                var count = 0
                while (true) {
                    val ce = s.nextCE(); assertEquals(ce, t.nextCE(), "CE/$numeric/$text/$count"); state(s, t)
                    if (ce == Collation.NO_CE) break
                    assertTrue(++count < 5000)
                }
                val so = SourceOffsets(); val to = UVector32()
                while (true) {
                    val ce = s.previousCE(so); assertEquals(ce, t.previousCE(to), "reverse/$text"); state(s, t)
                    assertEquals(so.size(), to.size()); assertContentEquals(so.buffer, to.getBuffer())
                    if (ce == Collation.NO_CE) break
                    assertTrue(++count < 10000)
                }
            }
        }
    }
    @Test fun codePointDirectionChangesResetsAndRawSegmentState() {
        val sd = com.ibm.icu.impl.coll.CollationRoot.getData(); val td = bridge.importSource(sd)
        val random = Random(794)
        for (text in texts()) {
            val s = Source(sd, false, text, 0); val t = FCDUTF16CollationIterator(td, false, text, 0)
            repeat(200) { step ->
                when (random.nextInt(10)) {
                    in 0..4 -> assertEquals(s.nextCodePoint(), t.nextCodePoint(), "next/$text/$step")
                    in 5..8 -> assertEquals(s.previousCodePoint(), t.previousCodePoint(), "prev/$text/$step")
                    else -> { val p = random.nextInt(text.length + 1); s.resetToOffset(p); t.resetToOffset(p) }
                }
                state(s, t)
            }
            s.setText(true, text, text.length); t.setText(true, text, text.length)
            while (true) { val cp = s.previousCodePoint(); assertEquals(cp, t.previousCodePoint()); state(s,t); if (cp < 0) break }
        }
    }
    @Test fun partialNullInvalidOffsetsHashAndUpstreamRecursiveEquals() {
        val sd = com.ibm.icu.impl.coll.CollationRoot.getData(); val td = bridge.importSource(sd)
        val s = Source(sd); val t = FCDUTF16CollationIterator(td)
        assertEquals(s.nextCodePoint(), t.nextCodePoint()); assertEquals(s.previousCodePoint(), t.previousCodePoint())
        assertEquals(outcome { s.resetToOffset(0) }, outcome { t.resetToOffset(0) })
        assertEquals(outcome { s.hashCode() }, outcome { t.hashCode() })
        assertFalse(s.equals(null)); assertFalse(t.equals(null))
        assertFailsWith<StackOverflowError> { s.equals(s) }; assertFailsWith<StackOverflowError> { t.equals(t) }
        for (p in listOf(-1, 0, 1, 2, Int.MAX_VALUE)) {
            s.setText(false,"a",p); t.setText(false,"a",p)
            assertEquals(outcome { s.nextCodePoint() }, outcome { t.nextCodePoint() }); state(s,t)
            assertEquals(outcome { s.previousCodePoint() }, outcome { t.previousCodePoint() }); state(s,t)
        }
        assertEquals(outcome { s.setText(false, null, 7) }, outcome { t.setText(false, null, 7) }); state(s,t)
    }
    private fun texts(): List<String> {
        val random = Random(783)
        val pieces = listOf("a", "ch", "é", "\u0315\u0300", "\u0301\u0323", "\u0345\u0324\u0301", "\u0f73", "\u0f75", "\u0f81", "😀", "\ud800", "\udc00", "\u1d15\u0300", "𝅗𝅥𝅥", "𝟘𝟙𝟚", "각", "١2", "\u0000")
        return listOf("", "abc", "a\u0315\u0300b", "\u0f73\u0f75\u0f81", "é\u0323", "\ud800\u0315\u0300\udc00", "ch\u0315\u0300", "123".repeat(100)) + List(50) { buildString { repeat(random.nextInt(1, 25)) { append(pieces.random(random)) } } }
    }
    private fun state(s: Source, t: FCDUTF16CollationIterator) {
        assertEquals(s.offset, t.getOffset()); assertEquals(s.getCEsLength(), t.getCEsLength()); assertContentEquals(s.getCEs(), t.getCEs())
        for (name in fields) assertEquals(value(Source::class.java, s, name), value(FCDUTF16CollationIterator::class.java, t, name), name)
        for (name in parentFields) assertEquals(value(SourceUTF16::class.java, s, name), value(UTF16CollationIterator::class.java, t, name), name)
        val sourceSeq = SourceUTF16::class.java.getDeclaredField("seq").also { it.isAccessible = true }.get(s)
        val sourceRaw = Source::class.java.getDeclaredField("rawSeq").also { it.isAccessible = true }.get(s)
        val targetSeq = UTF16CollationIterator::class.java.getDeclaredField("seq").also { it.isAccessible = true }.get(t)
        val targetRaw = FCDUTF16CollationIterator::class.java.getDeclaredField("rawSeq").also { it.isAccessible = true }.get(t)
        assertEquals(sourceSeq === sourceRaw, targetSeq === targetRaw)
    }
    private fun value(type: Class<*>, obj: Any, name: String): Any? = type.getDeclaredField(name).also { it.isAccessible = true }.get(obj).let { if (it is CharSequence) it.toString() else it }
    private fun outcome(block: () -> Any?): String = try { block().toString() } catch (e: Throwable) {
        when (e) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> e.javaClass.simpleName + ":" + e.message }
    }
}
