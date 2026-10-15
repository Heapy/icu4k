package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.StringSegment as JSegment
import com.ibm.icu.text.UnicodeSet as JSet
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.test.*

class StringSegmentOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
        when (e) { is AssertionError -> "assert"; is IndexOutOfBoundsException -> "bounds"; is NullPointerException -> "null"; else -> e.javaClass.simpleName }
    }
    private fun state(j: JSegment, k: StringSegment) {
        assertEquals(j.offset, k.getOffset()); assertEquals(j.length, k.length)
        assertEquals(outcome { j.toString() }, outcome { k.toString() })
        assertEquals(outcome { j.asString() }, outcome { k.asString() })
        assertEquals(outcome { j.codePoint }, outcome { k.getCodePoint() })
        for (i in -1..maxOf(j.length, 0) + 1) {
            assertEquals(outcome { j[i] }, outcome { k[i] }, "char $i")
            assertEquals(outcome { j.codePointAt(i) }, outcome { k.codePointAt(i) }, "cp $i")
        }
    }
    @Test fun windowsLogicalEndsSurrogatesAndFailedMutations() {
        for (text in listOf("", "abc", "XX😀ABC", "a\ud800x\udc00b", "𐐀𐐨")) for (fold in listOf(false, true)) {
            for (start in -1..text.length) for (length in -1..text.length + 1) {
                val j = JSegment(text, fold); val k = StringSegment(text, fold)
                assertEquals(outcome { j.offset = start }, outcome { k.setOffset(start) })
                assertEquals(outcome { j.setLength(length) }, outcome { k.setLength(length) })
                state(j, k)
                assertEquals(outcome { j.adjustOffsetByCodePoint() }, outcome { k.adjustOffsetByCodePoint() })
                state(j, k)
                assertEquals(outcome { j.resetLength() }, outcome { k.resetLength() })
                for (delta in listOf(-2, 0, 1, 2)) {
                    assertEquals(outcome { j.adjustOffset(delta) }, outcome { k.adjustOffset(delta) }); state(j, k)
                }
            }
        }
    }
    @Test fun foldsPrefixesAndUnicodeSets() {
        val texts = listOf("", "abc", "ABC", "İi", "Iı", "Σςσ", "ßss", "ẞ", "𐐀xy", "𐐨x", "😀a", "\ud800x")
        for (text in texts) for (fold in listOf(false, true)) for (other in texts + listOf(null)) {
            val j = JSegment(text, fold); val k = StringSegment(text, fold)
            assertEquals(outcome { j.startsWith(other as CharSequence?) }, outcome { k.startsWith(other as CharSequence?) }, "$text/$fold/$other")
            assertEquals(outcome { j.getCommonPrefixLength(other) }, outcome { k.getCommonPrefixLength(other) }, "$text/$fold/$other prefix")
            assertEquals(outcome { j.getCaseSensitivePrefixLength(other) }, outcome { k.getCaseSensitivePrefixLength(other) })
            assertEquals(j.contentEquals(other), k.contentEquals(other))
            for (cp in listOf(-1, 0, 65, 97, 0x10400, 0x10428, 0x1f600, 0x110000))
                assertEquals(outcome { j.startsWith(cp) }, outcome { k.startsWith(cp) })
            assertEquals(outcome { j.startsWith(JSet("[A-Z😀]")) }, outcome { k.startsWith(UnicodeSet("[A-Z😀]")) })
        }
    }
    @Test fun charSequenceCallbackOrder() {
        fun sequence(text: String, events: MutableList<String>) = object : CharSequence {
            override val length: Int get() { events.add("length"); return text.length }
            override fun get(index: Int): Char { events.add("get:$index"); return text[index] }
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence { events.add("slice"); return text.subSequence(startIndex, endIndex) }
        }
        for (a in listOf("abc", "😀x", "𐐀X", "")) for (b in listOf("abD", "😀y", "𐐨x", "")) {
            val je = mutableListOf<String>(); val ke = mutableListOf<String>()
            val j = JSegment(a, true); val k = StringSegment(a, true)
            val js = sequence(b, je); val ks = sequence(b, ke)
            for (operation in 0..2) {
                je.clear(); ke.clear()
                val jo = outcome { when (operation) { 0 -> j.startsWith(js); 1 -> j.getCommonPrefixLength(js); else -> j.contentEquals(js) } }
                val ko = outcome { when (operation) { 0 -> k.startsWith(ks); 1 -> k.getCommonPrefixLength(ks); else -> k.contentEquals(ks) } }
                assertEquals(jo, ko); assertEquals(je, ke, "$a/$b/$operation")
            }
        }
    }
}
