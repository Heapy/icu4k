// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import com.ibm.icu.text.RuleBasedCollator as Source
import com.ibm.icu.text.RawCollationKey as SourceKey
import io.github.heapy.icu4k.text.RawCollationKey
import kotlin.random.Random
import kotlin.test.*

class RuleBasedCollatorCoreOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        val t=if(e is java.lang.reflect.InvocationTargetException) e.targetException else e
        when(t) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> t.javaClass.simpleName+":"+t.message }
    }
    private val texts=listOf("", "abc", "ab", "AbC", "ab\u034f", "ab\ufffe", "æ", "ä", "ae", "é\u0327", "e\u0327\u0301", "ch", "chu", "xch", "😀", "𠀀", "각", "각", "12", "2", "002", "٤٥٦", "\u0000", "\ufffe", "\uffff", charArrayOf(0xd800.toChar()).concatToString(), charArrayOf(0xdc00.toChar()).concatToString())
    @Test fun comparisonAndSortKeyMatrixAgainstIcu4j() {
        for(rule in listOf("", "&c<ch<cu&a=b<æ/e<é", "[import de-u-co-phonebk]", "[backwards 2]&a<à<á", "[alternate shifted][caseLevel on][caseFirst upper]&a<ä")) {
            val s=Source(rule); val t=CollationBuilder(CollationRoot.getRoot()).parseAndBuild(rule)
            val settings=t.settings!!.copyOnWrite()!!; val c=collatorCore(t)
            for(strength in listOf(0,1,2,3,15)) for(numeric in listOf(false,true)) for(fcd in listOf(false,true)) {
                s.strength=strength; s.numericCollation=numeric; s.decomposition=if(fcd) 17 else 16
                settings.setStrength(strength); settings.setFlag(CollationSettings.NUMERIC,numeric); settings.setFlag(CollationSettings.CHECK_FCD,fcd)
                settings.fastLatinOptions=CollationFastLatin.getOptions(t.data,settings,settings.fastLatinPrimaries)
                val label="$rule/$strength/$numeric/$fcd"
                for(a in texts) {
                    assertContentEquals(s.getCollationKey(a).toByteArray(),c.getCollationKey(a)!!.toByteArray(),"key/$label/$a")
                    assertContentEquals(s.internalGetCEs(a),c.internalGetCEs(a),"CEs/$label/$a")
                    for(b in texts) assertEquals(s.compare(a,b),c.doCompare(a,b),"compare/$label/$a/$b")
                }
            }
        }
    }
    @Test fun rawKeyCapacityTailBytesOwnershipAndNullFailures() {
        val s=Source("[strength I]"); val t=CollationBuilder(CollationRoot.getRoot()).parseAndBuild("[strength I]"); val c=collatorCore(t)
        for(capacity in listOf(-1,0,1,5,200,2000)) {
            val sk=if(capacity<0) SourceKey() else SourceKey(capacity)
            val tk=if(capacity<0) RawCollationKey() else RawCollationKey(capacity)
            for(text in texts+listOf("é😀\ufffe".repeat(500),"a".repeat(300),"")) {
                assertSame(sk,s.getRawCollationKey(text,sk)); assertSame(tk,c.getRawCollationKey(text,tk))
                assertEquals(sk.size,tk.size); assertContentEquals(sk.bytes,tk.bytes,"capacity/$capacity/$text")
            }
            assertNull(s.getRawCollationKey(null,sk)); assertNull(c.getRawCollationKey(null,tk))
            assertEquals(sk.size,tk.size); assertContentEquals(sk.bytes,tk.bytes)
        }
        val compare=Source::class.java.getDeclaredMethod("doCompare",CharSequence::class.java,CharSequence::class.java).apply { isAccessible=true }
        for(a in listOf<CharSequence?>(null,"",StringBuilder("é"),StringBuilder("e\u0301"))) for(b in listOf<CharSequence?>(null,"",StringBuilder("é"),StringBuilder("e\u0301"))) {
            assertEquals(outcome { compare.invoke(s,a,b) },outcome { c.doCompare(a,b) })
        }
        assertEquals(outcome { s.internalGetCEs(null) },outcome { c.internalGetCEs(null) })
        assertContentEquals(s.getCollationKey("abc").toByteArray(),c.getCollationKey("abc")!!.toByteArray())
    }
    @Test fun freezingInsideCharSequencePreservesReleaseFailure() {
        val s=Source(""); val c=collatorCore(CollationRoot.getRoot())
        fun text(freeze: () -> Unit) = object : CharSequence {
            private var triggered=false
            override val length: Int get() { if(!triggered) { triggered=true; freeze() }; return 3 }
            override fun get(index: Int): Char = "abc"[index]
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = "abc".subSequence(startIndex,endIndex)
        }
        assertEquals(outcome { s.internalGetCEs(text { s.freeze() }) },outcome { c.internalGetCEs(text { c.freeze() }) })
        assertTrue(c.isFrozen())
        // Failure did not strand an acquired lock or invalidate subsequent calls.
        assertContentEquals(s.getCollationKey("abc").toByteArray(),c.getCollationKey("abc")!!.toByteArray())
    }
    @Test fun prefixBacktrackingReorderingAndFrozenConcurrentAccess() {
        val s=Source("&c<ch<cu&a<😀|b"); val t=CollationBuilder(CollationRoot.getRoot()).parseAndBuild("&c<ch<cu&a<😀|b"); val c=collatorCore(t)
        val settings=t.settings!!.copyOnWrite()!!
        s.setReorderCodes(17,25); settings.setReordering(t.data,intArrayOf(17,25))
        settings.fastLatinOptions=CollationFastLatin.getOptions(t.data,settings,settings.fastLatinPrimaries)
        val random=Random(7852)
        val samples=List(150) { "prefix" + List(random.nextInt(1,8)) { listOf("ch","cu","é","\u0327","😀b","12","一").random(random) }.joinToString("") }
        val expected=samples.map { s.getCollationKey(it).toByteArray() }
        for(i in samples.indices) {
            assertContentEquals(expected[i],c.getCollationKey(samples[i])!!.toByteArray())
            for(j in samples.indices) assertEquals(s.compare(samples[i],samples[j]),c.doCompare(samples[i],samples[j]))
        }
        s.freeze(); c.freeze(); assertTrue(c.isFrozen())
        val pool=java.util.concurrent.Executors.newFixedThreadPool(6)
        try {
            val jobs=(0..<600).map { n -> pool.submit<Unit> {
                val i=n%samples.size; val j=(n*17)%samples.size
                assertContentEquals(expected[i],c.getCollationKey(samples[i])!!.toByteArray())
                assertEquals(s.compare(samples[i],samples[j]),c.doCompare(samples[i],samples[j]))
            } }
            for(job in jobs) job.get(30,java.util.concurrent.TimeUnit.SECONDS)
        } finally { pool.shutdownNow() }
    }
}
