package io.github.heapy.icu4k.text

import com.ibm.icu.text.RuleBasedBreakIterator as J
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.util.BinaryBuffer
import kotlin.random.Random
import kotlin.test.*

class RuleBasedBreakIteratorOracleTest {
    private fun compareCall(j: () -> Any?, k: () -> Any?) {
        val expected = runCatching(j)
        val actual = runCatching(k)
        if (expected.isSuccess) assertEquals(expected.getOrThrow(), actual.getOrThrow())
        else {
            val e = expected.exceptionOrNull()!!
            val a = actual.exceptionOrNull() ?: fail("Expected ${e.javaClass.name}, got ${actual.getOrNull()}")
            if (e is IndexOutOfBoundsException) assertTrue(a is IndexOutOfBoundsException)
            else assertEquals(e.javaClass, a.javaClass)
        }
    }
    @Test fun publicNavigationStatusesTextOwnershipAndCloningMatchOriginal() {
        val random = Random(619085)
        val sources = listOf("", "A", "Hello, world! One two.\nNext line.", "a ".repeat(170),
            "日本語中文한국어ພາສາລາວខ្មែរ ภาษาไทยမြန်မာဘာသာစကား 😀!".repeat(3),
            "A\ud800B\udc00C😀\u0301é".repeat(10))
        val rules = listOf("char", "word", "word_POSIX", "line", "line_cj", "line_loose", "line_loose_cj", "line_normal",
            "line_normal_cj", "line_phrase_cj", "line_loose_phrase_cj", "line_normal_phrase_cj", "sent", "sent_el", "title")
        for (rule in rules) for (phrase in listOf(false, true)) {
            val raw = ICUData.getBytes("brkitr/$rule.brk")!!
            val j = J.getInstanceFromCompiledRules(java.nio.ByteBuffer.wrap(raw))
            J::class.java.getDeclaredField("fPhraseBreaking").also { it.isAccessible = true }.setBoolean(j, phrase)
            val k = RuleBasedBreakIterator.getInstanceFromCompiledRules(BinaryBuffer(raw), phrase)
            fun state(a: J = j, b: RuleBasedBreakIterator = k) {
                assertEquals(a.current(), b.current(), "$rule phrase=$phrase")
                assertEquals(a.ruleStatus, b.getRuleStatus())
                for (size in 0..3) {
                    val av = IntArray(size) { -1 }; val bv = av.copyOf()
                    assertEquals(a.getRuleStatusVec(av), b.getRuleStatusVec(bv)); assertContentEquals(av, bv)
                }
                assertEquals(a.getRuleStatusVec(null), b.getRuleStatusVec(null))
                assertEquals(a.text.index, b.getText().getIndex())
            }
            state()
            assertEquals(j.toString(), k.toString()); assertEquals(j.hashCode(), k.hashCode())
            for (source in sources) {
                val begin = if (source.isEmpty()) 0 else random.nextInt(0, minOf(3, source.length) + 1)
                val jt = java.text.StringCharacterIterator(source, begin, source.length, begin)
                val kt = StringCharacterIterator(source, begin, source.length, begin)
                j.setText(jt); k.setText(kt)
                assertSame(jt, j.text); assertSame(kt, k.getText()); state()
                repeat(source.length + 3) { compareCall({ j.next() }, { k.next() }); state() }
                repeat(source.length + 3) { compareCall({ j.previous() }, { k.previous() }); state() }
                for (offset in begin - 2..source.length + 2) {
                    compareCall({ j.following(offset) }, { k.following(offset) }); state()
                    compareCall({ j.preceding(offset) }, { k.preceding(offset) }); state()
                    if (offset in begin..source.length) { compareCall({ j.isBoundary(offset) }, { k.isBoundary(offset) }); state() }
                    else {
                        assertFailsWith<IndexOutOfBoundsException> { j.isBoundary(offset) }
                        assertFailsWith<IndexOutOfBoundsException> { k.isBoundary(offset) }; state()
                    }
                }
                repeat(60) {
                    when (random.nextInt(5)) {
                        0 -> compareCall({ j.first() }, { k.first() })
                        1 -> compareCall({ j.last() }, { k.last() })
                        2 -> { val n = random.nextInt(-6, 7); compareCall({ j.next(n) }, { k.next(n) }) }
                        3 -> compareCall({ j.next() }, { k.next() })
                        else -> compareCall({ j.previous() }, { k.previous() })
                    }
                    state()
                    val jc = j.clone(); val kc = k.clone()
                    assertEquals(k, kc); assertNotSame(k.getText(), kc.getText())
                    repeat(3) { compareCall({ jc.next() }, { kc.next() }); state(jc, kc) }
                    state()
                }
            }
        }
    }
}
