// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.util.CharsTrieBuilder
import io.github.heapy.icu4k.util.StringTrieBuilder
import kotlin.test.*

class TailoredSetTest {
    @Test fun simpleCopiedMappingsAndExpansionContents() {
        val exp=Collation.makeCE32FromTagIndexAndLength(6,0,2)
        val base=iteratorFixtureData().apply { trie=tailoredFixtureTrie(mapOf(65 to iteratorSimple(65),66 to iteratorSimple(66),67 to exp)) }
        val child=iteratorFixtureData().apply {
            this.base=base;trie=tailoredFixtureTrie(mapOf(65 to iteratorSimple(90),66 to iteratorSimple(66),67 to exp))
        }
        val result=UnicodeSet();TailoredSet(result).forData(child)
        assertTrue(result.contains(65));assertFalse(result.contains(66));assertFalse(result.contains(67))
        child.ces=child.ces!!.copyOf().also { it[1]++ }
        val changed=UnicodeSet();TailoredSet(changed).forData(child);assertTrue(changed.contains(67))
    }
    @Test fun contextChangesAndSurrogatePrefixReversal() {
        val pair=charArrayOf(0xd83d.toChar(),0xde00.toChar()).concatToString()
        val base=iteratorFixtureData().apply { trie=tailoredFixtureTrie(mapOf(112 to iteratorSimple(112))) }
        for(prefix in listOf(pair,charArrayOf(0xde00.toChar(),0xd83d.toChar()).concatToString())) {
            val value=iteratorSimple(112)
            val child=iteratorFixtureData().apply {
                this.base=base
                contexts=charArrayOf((value ushr 16).toChar(),value.toChar()).concatToString()+CharsTrieBuilder().add(prefix,iteratorSimple(90)).buildCharSequence(StringTrieBuilder.Option.SMALL)
                trie=tailoredFixtureTrie(mapOf(112 to Collation.makeCE32FromTagAndIndex(8,0)))
            }
            val result=UnicodeSet();TailoredSet(result).forData(child)
            assertTrue(result.contains(pair+"p"));assertFalse(result.contains(112))
        }
    }
    @Test fun parallelPrefixAndSuffixComparison() {
        fun context(entries: Map<String,Int>): String {
            val value=iteratorSimple(65);val builder=CharsTrieBuilder()
            for((key,v) in entries) builder.add(key,v)
            return charArrayOf((value ushr 16).toChar(),value.toChar()).concatToString()+builder.buildCharSequence(StringTrieBuilder.Option.SMALL)
        }
        for(tag in listOf(8,9)) {
            val old=if(tag==8) mapOf("x" to iteratorSimple(1),"xy" to iteratorSimple(2),"w" to iteratorSimple(3)) else mapOf("a" to iteratorSimple(1),"ab" to iteratorSimple(2),"z" to iteratorSimple(3),"\uffff" to iteratorSimple(4))
            val next=if(tag==8) mapOf("x" to iteratorSimple(1),"xy" to iteratorSimple(4),"z" to iteratorSimple(2)) else mapOf("a" to iteratorSimple(1),"ab" to iteratorSimple(4),"b" to iteratorSimple(2))
            val base=iteratorFixtureData().apply { contexts=context(old);trie=tailoredFixtureTrie(mapOf(112 to Collation.makeCE32FromTagAndIndex(tag,0))) }
            val child=iteratorFixtureData().apply { this.base=base;contexts=context(next);trie=tailoredFixtureTrie(mapOf(112 to Collation.makeCE32FromTagAndIndex(tag,0))) }
            val result=UnicodeSet();TailoredSet(result).forData(child)
            val expected=if(tag==8) listOf("yxp","wp","zp") else listOf("pab","pb","pz","p\uffff")
            for(text in expected) assertTrue(result.contains(text),text)
            assertFalse(result.contains(if(tag==8) "xp" else "pa"));assertFalse(result.contains(112))
        }
    }
    @Test fun tailoredJamoMarksItsHangulSyllables() {
        val values=(0xac00..0xac1f).associateWith { 0xcc or Collation.HANGUL_NO_SPECIAL_JAMO }+mapOf(0x1100 to iteratorSimple(20))
        val base=iteratorFixtureData().apply { trie=tailoredFixtureTrie(values) }
        val child=iteratorFixtureData().apply { this.base=base;trie=tailoredFixtureTrie(values+mapOf(0x1100 to iteratorSimple(21))) }
        val result=UnicodeSet();TailoredSet(result).forData(child)
        assertTrue(result.contains(0x1100));assertTrue(result.contains(0xac00,0xac1f))
    }
}
internal fun tailoredFixtureTrie(values: Map<Int,Int>) = iteratorFixtureTrie(values,supplementary=Collation.FALLBACK_CE32)
