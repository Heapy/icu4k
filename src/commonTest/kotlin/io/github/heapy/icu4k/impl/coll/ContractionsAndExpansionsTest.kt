// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.util.CharsTrieBuilder
import io.github.heapy.icu4k.util.StringTrieBuilder
import kotlin.test.*

class ContractionsAndExpansionsTest {
    @Test fun fixtureContextsAndExpansions() {
        val data=iteratorFixtureData();val con=UnicodeSet();val exp=UnicodeSet();val calls=mutableListOf<List<Long>>()
        val sink=object: ContractionsAndExpansions.CESink {
            override fun handleCE(ce: Long) { calls.add(listOf(ce)) }
            override fun handleExpansion(ces: LongArray?,start: Int,length: Int) { calls.add(ces!!.slice(start until start+length)) }
        }
        val cne=ContractionsAndExpansions(con,exp,sink,true)
        cne.forCodePoint(data,'a'.code);assertTrue(con.contains("ab"));assertTrue(con.contains("abc"));assertTrue(exp.contains("abc"))
        cne.forCodePoint(data,'p'.code);assertTrue(con.contains("xp"));assertTrue(con.contains("yxp"));assertTrue(exp.contains("yxp"))
        for(c in listOf('e','f','g')) { cne.forCodePoint(data,c.code);assertTrue(exp.contains(c.code)) }
        assertTrue(calls.any { it.size==3 })
        val alias=object: ContractionsAndExpansions.CESink {
            override fun handleCE(ce: Long) { }
            override fun handleExpansion(ces: LongArray?,start: Int,length: Int) { assertSame(data.ces,ces);assertEquals(2,length) }
        }
        ContractionsAndExpansions(null,null,alias,false).forCodePoint(data,'g'.code)
    }
    @Test fun prefixReversalPreservesSurrogatesOnEveryTarget() {
        val pair=charArrayOf(0xd83d.toChar(),0xde00.toChar()).concatToString()
        val reversedPair=charArrayOf(0xde00.toChar(),0xd83d.toChar()).concatToString()
        for(prefix in listOf(pair,reversedPair,"x"+pair)) {
            val value=iteratorSimple(20)
            val contexts=charArrayOf((value ushr 16).toChar(),value.toChar()).concatToString()+CharsTrieBuilder().add(prefix,value).buildCharSequence(StringTrieBuilder.Option.SMALL)
            val d=iteratorFixtureData().apply { this.contexts=contexts;trie=iteratorFixtureTrie(mapOf('p'.code to Collation.makeCE32FromTagAndIndex(8,0))) }
            val set=UnicodeSet();ContractionsAndExpansions(set,null,null,true).forCodePoint(d,'p'.code)
            val expected=if(prefix.startsWith("x")) pair+"xp" else pair+"p"
            assertTrue(set.contains(expected))
        }
    }
    @Test fun noSinkStillCollectsHangulAndPrefixOption() {
        val d=iteratorFixtureData();val con=UnicodeSet();val exp=UnicodeSet()
        val cne=ContractionsAndExpansions(con,exp,null,false);cne.forCodePoint(d,'p'.code);assertTrue(con.isEmpty())
        cne.forCodePoint(d,0xac00);assertTrue(exp.contains(0xac00))
        val e=assertFailsWith<AssertionError> { cne.forCodePoint(d.apply { trie=iteratorFixtureTrie(mapOf(65 to 0xc3)) },65) }
        assertEquals("Unexpected CE32 tag type 3 for ce32=0x000000c3",e.message)
    }
}
