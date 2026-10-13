package io.github.heapy.icu4k.text

import com.ibm.icu.text.FilteredBreakIteratorBuilder as JB
import com.ibm.icu.text.RuleBasedBreakIterator as JI
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.util.BinaryBuffer
import kotlin.random.Random
import kotlin.test.*

class FilteredBreakIteratorOracleTest {
    private fun compareCall(j: () -> Any?, k: () -> Any?) {
        val expected = runCatching(j)
        val actual = runCatching(k)
        if (expected.isSuccess) assertEquals(expected.getOrThrow(), actual.getOrThrow())
        else {
            val je = expected.exceptionOrNull()!!
            val ke = actual.exceptionOrNull() ?: fail("Expected ${je.javaClass.name}")
            assertEquals(je.javaClass, ke.javaClass)
        }
    }
    @Test fun builderMutationTrieContentsAndNavigationMatchOriginal() {
        val raw = ICUData.getBytes("brkitr/sent.brk")!!
        val random = Random(381005)
        val filterSets = listOf(emptyList(), listOf("Mr."), listOf("Mr.", "Mrs.", "Dr.", "Ph.D.", "Ph.M.", "a.M.", "X.Y.Z."),
            listOf("Ph.", "Ph.D."), listOf("😀.", "😀.X.", "\ud800.", "x"), listOf("U.S.", "U.K.", "U.S.A."))
        for (filters in filterSets) {
            val jb = JB.getEmptyInstance(); val kb = FilteredBreakIteratorBuilder.getEmptyInstance()
            for (filter in filters) {
                assertEquals(jb.suppressBreakAfter(filter), kb.suppressBreakAfter(filter))
                assertEquals(jb.suppressBreakAfter(filter), kb.suppressBreakAfter(filter))
            }
            assertEquals(jb.unsuppressBreakAfter("missing"), kb.unsuppressBreakAfter("missing"))
            val jd = JI.getInstanceFromCompiledRules(java.nio.ByteBuffer.wrap(raw))
            val kd = RuleBasedBreakIterator.getInstanceFromCompiledRules(BinaryBuffer(raw))
            val j = jb.wrapIteratorWithFilter(jd); val k = kb.wrapIteratorWithFilter(kd)
            if (filters.isEmpty()) { assertSame(jd, j); assertSame(kd, k) }
            if (filters.isNotEmpty()) {
                fun trie(target: Any, name: String): List<Pair<String, Int>>? {
                    val value = target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target) ?: return null
                    return when (value) {
                        is com.ibm.icu.util.CharsTrie -> value.map { it.chars.toString() to it.value }
                        is io.github.heapy.icu4k.util.CharsTrie -> value.map { it.chars.toString() to it.value }
                        else -> error("unknown trie")
                    }
                }
                for (field in listOf("backwardsTrie", "forwardsPartialTrie")) assertEquals(trie(j, field), trie(k, field))
            }
            val texts = listOf("", "Mr. Smith. Mrs. Brown! Dr. Who? End.", "Ph.D. Next. Ph.M. Other. Ph.X. Fail.",
                "a.M. Brown. U.S.A. End. X.Y.Z. Tail.", "Mr.  Smith.\nMr.\tBrown. Mr.\r\nBrown.", "😀.X. Next. \ud800. End.",
                "Mr. Smith. ".repeat(80), "Mr.")
            for (text in texts) {
                j.setText(text); k.setText(text)
                fun state() {
                    assertEquals(j.current(), k.current(), "$filters / $text")
                    assertEquals(j.text.index, k.getText().getIndex())
                    assertEquals(j.ruleStatus, k.getRuleStatus())
                    val ja = IntArray(4) { -1 }; val ka = ja.copyOf()
                    assertEquals(j.getRuleStatusVec(ja), k.getRuleStatusVec(ka)); assertContentEquals(ja, ka)
                }
                compareCall({ j.first() }, { k.first() }); state()
                repeat(text.length + 2) { compareCall({ j.next() }, { k.next() }); state() }
                compareCall({ j.last() }, { k.last() }); state()
                repeat(text.length + 2) { compareCall({ j.previous() }, { k.previous() }); state() }
                for (offset in 0..text.length) {
                    compareCall({ j.following(offset) }, { k.following(offset) }); state()
                    compareCall({ j.preceding(offset) }, { k.preceding(offset) }); state()
                    compareCall({ j.isBoundary(offset) }, { k.isBoundary(offset) }); state()
                }
                repeat(30) { val n = random.nextInt(-5, 6); compareCall({ j.next(n) }, { k.next(n) }); state() }
                val jc = j.clone() as com.ibm.icu.text.BreakIterator; val kc = k.clone()
                compareCall({ j == jc }, { k == kc })
                repeat(5) { compareCall({ jc.next() }, { kc.next() }); assertEquals(jc.current(), kc.current()) }
                assertEquals(j.current(), k.current())
                compareCall({ j.hashCode(); true }, { k.hashCode(); true })
            }
            for (filter in filters) assertEquals(jb.unsuppressBreakAfter(filter), kb.unsuppressBreakAfter(filter))
            assertSame(jd, jb.wrapIteratorWithFilter(jd)); assertSame(kd, kb.wrapIteratorWithFilter(kd))
        }
    }

    @Test fun emptyDuplicateMutableAndBoundedInputsMatchOriginal() {
        val rules = "[a-zA-Z]+[.] ' '*{42};.;"
        val jb = JB.getEmptyInstance(); val kb = FilteredBreakIteratorBuilder.getEmptyInstance()
        assertEquals(jb.suppressBreakAfter(""), kb.suppressBreakAfter(""))
        compareCall({ jb.wrapIteratorWithFilter(JI(rules)); true }, { kb.wrapIteratorWithFilter(RuleBasedBreakIterator(rules)); true })
        val je = jb.wrapIteratorWithFilter(JI(rules)); val ke = kb.wrapIteratorWithFilter(RuleBasedBreakIterator(rules))
        compareCall({ je == je.clone() }, { ke == ke.clone() })
        je.setText("Mr. X."); ke.setText("Mr. X.")
        repeat(8) { compareCall({ je.next() }, { ke.next() }) }
        jb.unsuppressBreakAfter(""); kb.unsuppressBreakAfter("")
        val first = StringBuilder("Mr."); val second = StringBuilder("Mr.")
        for (s in listOf(first, first, second)) assertEquals(jb.suppressBreakAfter(s), kb.suppressBreakAfter(s))
        compareCall({ jb.wrapIteratorWithFilter(JI(rules)); true }, { kb.wrapIteratorWithFilter(RuleBasedBreakIterator(rules)); true })
        jb.unsuppressBreakAfter(first); kb.unsuppressBreakAfter(first)
        second.append('X')
        val j = jb.wrapIteratorWithFilter(JI(rules)); val k = kb.wrapIteratorWithFilter(RuleBasedBreakIterator(rules))
        val text = "skip Mr.X. Next. end"
        j.setText(java.text.StringCharacterIterator(text, 5, text.length - 4, 5))
        k.setText(StringCharacterIterator(text, 5, text.length - 4, 5))
        repeat(text.length) { compareCall({ j.next() }, { k.next() }); assertEquals(j.current(), k.current()) }
        repeat(text.length) { compareCall({ j.previous() }, { k.previous() }); assertEquals(j.current(), k.current()) }
        for (offset in 5..text.length - 4) compareCall({ j.isBoundary(offset) }, { k.isBoundary(offset) })
    }
}
