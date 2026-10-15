// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*
import com.ibm.icu.impl.coll.CollationData as SourceData
import com.ibm.icu.impl.coll.CollationIterator as SourceBase
import com.ibm.icu.impl.coll.UTF16CollationIterator as Source
import com.ibm.icu.impl.coll.UVector32 as SourceOffsets
import com.ibm.icu.impl.coll.CollationRoot as SourceRoot

class CollationIteratorOracleTest {
    private val bridge = CollationDataOracleTest()
    @Test fun rootAndTailoredDataForwardBackwardBuffersAndOffsets() {
        val sources = mutableListOf(SourceRoot.getData())
        val dataField = com.ibm.icu.text.RuleBasedCollator::class.java.getDeclaredField("data").also { it.isAccessible = true }
        for (id in listOf("es@collation=traditional", "de@collation=phonebook", "cs", "th"))
            sources.add(dataField.get(com.ibm.icu.text.Collator.getInstance(com.ibm.icu.util.ULocale(id))) as SourceData)
        val random = Random(7803)
        val pieces = listOf("a", "b", "ch", "ll", "é", "e\u0301", "a\u0323\u0301", "a\u0301\u0323", "각", "각", "😀", "𐐨", "\ud800", "\udc00", "\u0000", "\u0f73", "一", "๑", "١", "1", "0", " ")
        val texts = listOf("", "Abc xyz 123", "chll", "é e\u0301 a\u0323\u0301", "😀\ud800A\udc00", "각가각", "0000010234", "1" + "0".repeat(254), "0123456789".repeat(60), "ß".repeat(100), "\u0f73\u0f75\u0f81") + List(60) { buildString { repeat(random.nextInt(1, 24)) { append(pieces.random(random)) } } }
        for (data in sources) {
            val targetData = bridge.importSource(data)
            for (numeric in listOf(false, true)) for (text in texts) compareIteration(data, targetData, numeric, text)
        }
    }
    @Test fun syntheticAllTagsContextsNumericAndLeadSurrogatePaths() {
        val target = iteratorFixtureData()
        val source = bridge.exportTarget(target)
        for (numeric in listOf(false, true)) for (text in listOf("abc abx axp yxp", "e f g o", "a\u0323\u0301", "a\u0323\u0300\u0301", "a\u0300\u0301", "a\u0323x", "ab\u0323\u0301", "a\u0323\u0301a\u0323\u0301", "각가", "\u0000123", "0", "73", "74", "10233", "10234", "1042489", "1042490", "1234567", "0".repeat(600), "1234567890".repeat(100))) compareIteration(source, target, numeric, text)
        val nested = iteratorNestedFixtureData()
        val nestedSource = bridge.exportTarget(nested)
        for (numeric in listOf(false, true)) for (text in listOf("a\u0323\u0324\u0301\u0345", "a\u0323\u0345X", "a\u0323\u0324\u0345", "a\u0323\u0324\u0345\u0301", "a\u0323\u0324\u0301\u0345".repeat(3))) compareIteration(nestedSource, nested, numeric, text)
        val slow = iteratorFixtureData()
        slow.jamoCE32s!![0] = Collation.makeCE32FromTagIndexAndLength(5, 10, 3)
        slow.trie = iteratorFixtureTrie(mapOf(0xac00 to 0xcc, 0xac01 to 0xcc))
        compareIteration(bridge.exportTarget(slow), slow, false, "가각")
        for (kind in listOf(0, 0x100, 0x200)) for (supp in listOf(0x100, 0x12340505, Collation.FALLBACK_CE32, Collation.UNASSIGNED_CE32)) {
            val d = iteratorFixtureData()
            d.trie = iteratorFixtureTrie(emptyMap(), 0xcd or kind, supp)
            d.base = iteratorFixtureData()
            val s = bridge.exportTarget(d)
            // Single-lead and code-point data may intentionally differ in this fixture: compare each direction with ICU4J.
            compareIteration(s, d, false, "😀\ud800\udc00\ud800x\udc00")
        }
    }
    @Test fun partialConstructorsEqualityHistoryResetsAndFailures() {
        val d = iteratorFixtureData(); val sd = bridge.exportTarget(d)
        val s = Source(sd); val t = UTF16CollationIterator(d)
        assertEquals(s.offset, t.getOffset())
        assertEquals(outcome { s == s }, outcome { t == t })
        assertEquals(outcome { s.nextCE() }, outcome { t.nextCE() })
        assertEquals(outcome { s.resetToOffset(1) }, outcome { t.resetToOffset(1) })
        s.setText(false, "g", 0); t.setText(false, "g", 0)
        assertEquals(s.nextCE(), t.nextCE()); state(s, t)
        val sArray = s.getCEs(); val tArray = t.getCEs()
        sArray[1] = 99; tArray[1] = 99
        assertEquals(s.nextCE(), t.nextCE())
        s.clearCEsIfNoneRemaining(); t.clearCEsIfNoneRemaining(); state(s, t)
        for (pos in listOf(-1, 0, 1, 2, Int.MAX_VALUE)) {
            s.setText(true, "A", pos); t.setText(true, "A", pos)
            assertEquals(outcome { s.nextCodePoint() }, outcome { t.nextCodePoint() })
            assertEquals(s.offset, t.getOffset())
            assertEquals(outcome { s.previousCodePoint() }, outcome { t.previousCodePoint() })
            assertEquals(s.offset, t.getOffset())
        }
        assertEquals(outcome { s.setText(false, null, 7) }, outcome { t.setText(false, null, 7) })
        assertEquals(s.offset, t.getOffset()); state(s, t)
        assertEquals(outcome { s.hashCode() }, outcome { t.hashCode() })
        val a = Source(sd, false, "A", 0); val b = Source(sd, false, "B", 0)
        val x = UTF16CollationIterator(d, false, "A", 0); val y = UTF16CollationIterator(d, false, "B", 0)
        assertEquals(a == b, x == y) // Text and data are deliberately ignored.
        a.nextCE(); x.nextCE(); assertEquals(a == b, x == y)
        b.nextCE(); y.nextCE(); assertEquals(a == b, x == y)
        // A failure before nextCEFromCE32 leaves the reserved CE slot visible.
        val error = IllegalStateException("handleNextCE32")
        val sf = object : Source(sd, false, "B", 0) { var fail = true; override fun handleNextCE32(): Long { if (fail) { fail = false; throw error }; return super.handleNextCE32() } }
        val tf = object : UTF16CollationIterator(d, false, "B", 0) { var fail = true; override fun handleNextCE32(): Long { if (fail) { fail = false; throw error }; return super.handleNextCE32() } }
        assertSame(error, assertFailsWith<IllegalStateException> { sf.nextCE() }); assertSame(error, assertFailsWith<IllegalStateException> { tf.nextCE() })
        state(sf, tf); assertEquals(sf.nextCE(), tf.nextCE()); assertEquals(sf.offset, tf.getOffset())
    }
    @Test fun protectedBuilderAndForbiddenSurrogateHooks() {
        val d = iteratorFixtureData().also { it.trie = iteratorFixtureTrie(mapOf('B'.code to 0xc7)); it.base = iteratorFixtureData() }
        val sd = bridge.exportTarget(d)
        val s = object : Source(sd, false, "B\ud800", 0) { override fun getCE32FromBuilderData(ce32: Int): Int = Collation.FALLBACK_CE32; override fun forbidSurrogateCodePoints(): Boolean = true }
        val t = object : UTF16CollationIterator(d, false, "B\ud800", 0) { override fun getCE32FromBuilderData(ce32: Int): Int = Collation.FALLBACK_CE32; override fun forbidSurrogateCodePoints(): Boolean = true }
        while (true) { val ce = s.nextCE(); assertEquals(ce, t.nextCE()); if (ce == Collation.NO_CE) break }
        val badS = Source(sd, false, "B", 0); val badT = UTF16CollationIterator(d, false, "B", 0)
        assertEquals(outcome { badS.nextCE() }, outcome { badT.nextCE() }); state(badS, badT)
    }
    @Test fun partialProtectedAppendTagErrorsAndBuilderCallbacksPrecedeBufferAccess() {
        val d = iteratorFixtureData().apply { ces = longArrayOf() }; val sd = bridge.exportTarget(d)
        val source = object : Source(sd) {
            var calls = 0
            fun appendRaw(ce32: Int) { appendCEsFromCE32(sd, 65, ce32, true) }
            override fun getCE32FromBuilderData(ce32: Int): Int { ++calls; return iteratorSimple(65) }
        }
        val target = object : UTF16CollationIterator(d) {
            var calls = 0
            fun appendRaw(ce32: Int) { appendCEsFromCE32(d, 65, ce32, true) }
            override fun getCE32FromBuilderData(ce32: Int): Int { ++calls; return iteratorSimple(65) }
        }
        for (ce32 in listOf(0xc0, 0xc3, 0xc7, 0x12340505, 0x123456c1, Collation.makeCE32FromTagAndIndex(14, 0))) {
            assertEquals(outcome { source.appendRaw(ce32) }, outcome { target.appendRaw(ce32) })
            assertEquals(source.calls, target.calls)
            assertEquals(outcome { source.getCEsLength() }, outcome { target.getCEsLength() })
        }
    }
    private fun compareIteration(sd: SourceData, td: CollationData, numeric: Boolean, text: String) {
        val source = Source(sd, numeric, text, 0); val target = UTF16CollationIterator(td, numeric, text, 0)
        var count = 0
        while (true) {
            val s = source.nextCE(); val t = target.nextCE()
            assertEquals(s, t, "forward/$numeric/$text/$count")
            assertEquals(source.offset, target.getOffset()); state(source, target)
            if (s == Collation.NO_CE) break
            if (++count > text.length * 100 + 100) fail("Unbounded fixture iteration")
        }
        assertEquals(source.nextCE(), target.nextCE()); state(source, target)
        source.setText(numeric, text, 0); target.setText(numeric, text, 0)
        assertEquals(source.fetchCEs(), target.fetchCEs()); state(source, target)
        source.setText(numeric, text, text.length); target.setText(numeric, text, text.length)
        val so = SourceOffsets(); val to = UVector32()
        count = 0
        while (true) {
            val s = source.previousCE(so); val t = target.previousCE(to)
            assertEquals(s, t, "backward/$numeric/$text/$count")
            assertEquals(source.offset, target.getOffset()); state(source, target)
            assertEquals(so.size(), to.size()); assertContentEquals(so.getBuffer(), to.getBuffer())
            if (s == Collation.NO_CE) break
            if (++count > text.length * 100 + 100) fail("Unbounded fixture iteration")
        }
    }
    private fun state(source: Source, target: UTF16CollationIterator) {
        assertEquals(source.getCEsLength(), target.getCEsLength())
        assertContentEquals(source.getCEs(), target.getCEs())
    }
    private fun outcome(block: () -> Any?): Any? = try { block(); "ok" } catch (e: Throwable) {
        val cause = if (e is InvocationTargetException) e.targetException else e
        when (cause) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> "${cause.javaClass.simpleName}:${cause.message}" }
    }
}
