// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import kotlin.random.Random
import kotlin.test.*
import com.ibm.icu.impl.coll.CollationCompare as SourceCompare
import com.ibm.icu.impl.coll.CollationSettings as SourceSettings
import com.ibm.icu.impl.coll.CollationIterator as SourceIterator
import com.ibm.icu.impl.coll.CollationData as SourceData
import com.ibm.icu.impl.coll.UTF16CollationIterator as SourceUTF16
import com.ibm.icu.impl.coll.FCDUTF16CollationIterator as SourceFCD

class CollationCompareOracleTest {
    private val bridge=CollationDataOracleTest()
    private fun sourceSettings(s: CollationSettings): SourceSettings = SourceSettings::class.java.getDeclaredConstructor().also { it.isAccessible=true }.newInstance().also { source ->
        for (name in listOf("options","variableTop","reorderTable","reorderCodes","reorderRanges","minHighNoReorder")) {
            SourceSettings::class.java.getDeclaredField(name).also { it.isAccessible=true }.set(source,CollationSettings::class.java.getDeclaredField(name).also { it.isAccessible=true }.get(s))
        }
    }
    private fun outcome(block: () -> Int): Any = try { block() } catch(e: Throwable) {
        when(e) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> e.javaClass.simpleName+":"+e.message }
    }
    private fun state(s: SourceIterator?,t: CollationIterator?) {
        if (s == null || t == null) return
        assertEquals(outcome { s.offset },outcome { t.getOffset() })
        assertEquals(outcome { s.getCEsLength() },outcome { t.getCEsLength() })
        assertContentEquals(s.getCEs(),t.getCEs())
        val si=SourceIterator::class.java.getDeclaredField("cesIndex").also { it.isAccessible=true }.get(s)
        val ti=CollationIterator::class.java.getDeclaredField("cesIndex").also { it.isAccessible=true }.get(t)
        assertEquals(si,ti)
    }
    private fun compare(sl: SourceIterator?,sr: SourceIterator?,tl: CollationIterator?,tr: CollationIterator?,settings: CollationSettings?,label: String="") {
        val ss=settings?.let { sourceSettings(it) }
        assertEquals(outcome { SourceCompare.compareUpToQuaternary(sl,sr,ss) },outcome { CollationCompare.compareUpToQuaternary(tl,tr,settings) },label)
        state(sl,tl); state(sr,tr)
    }
    @Test fun realRootAndTailoringsAcrossAllComparisonOptions() {
        val root=com.ibm.icu.impl.coll.CollationRoot.getData()
        val dataField=com.ibm.icu.text.RuleBasedCollator::class.java.getDeclaredField("data").also { it.isAccessible=true }
        val sources=listOf(root)+listOf("fr","es@collation=traditional","de@collation=phonebook","th").map { dataField.get(com.ibm.icu.text.Collator.getInstance(com.ibm.icu.util.ULocale(it))) as SourceData }
        val random=Random(78344)
        val pieces=listOf("a","A","ch","ll","é","e\u0301","a\u0315\u0300","\u0f73","😀","\ud800","\udc00","角","각","0","12","١","𝟘","-"," ","\ufffe")
        val pairs=listOf("a" to "A","é" to "e\u0301","a b" to "ab","12" to "2","ch" to "d","ä" to "ae","côte" to "coté","a\ufffeb" to "a\ufffec","a\u0315\u0300" to "a\u0300\u0315","" to " ")+List(50) { val prefix=buildString { repeat(random.nextInt(0,8)) { append(pieces.random(random)) } }; prefix+pieces.random(random) to prefix+pieces.random(random) }
        for (sd in sources) {
            val td=bridge.importSource(sd)
            for (strength in listOf(0,1,2,3,15)) for (case in listOf(0,0x200,0x300,0x400,0x700)) for (mode in 0..7) {
                val settings=CollationSettings().apply {
                    options=(strength shl 12) or case or (if ((mode and 1)!=0) 4 else 0) or (if ((mode and 2)!=0) 0x800 else 0) or (if ((mode and 4)!=0) 2 else 0)
                    variableTop=td.getLastPrimaryForGroup(4097)
                    if ((mode and 3)==3) setReordering(td,intArrayOf(14,25))
                }
                for ((a,b) in pairs) {
                    val sl=SourceFCD(sd,settings.isNumeric(),a,0); val sr=SourceFCD(sd,settings.isNumeric(),b,0)
                    val tl=FCDUTF16CollationIterator(td,settings.isNumeric(),a,0); val tr=FCDUTF16CollationIterator(td,settings.isNumeric(),b,0)
                    compare(sl,sr,tl,tr,settings,"$strength/$case/$mode/$a/$b")
                }
            }
        }
    }
    @Test fun syntheticWeightsQuaternaryAndShiftedBufferMutation() {
        val random=Random(87344)
        val merge=Collation.ceFromSimpleCE32(Collation.MERGE_SEPARATOR_CE32)
        val weights=listOf(0L,compareCE(0,0x500),compareCE(0,0,0x8500),compareCE(0x10000000),compareCE(0x30000000),compareCE(0x30000000,0x600),compareCE(0x30000000,0x500,0x8500),compareCE(0x30000000,0x500,0x540),compareCE(0x30000000,0x500,0x580),merge)
        val pairs=weights.flatMap { a -> weights.map { b -> longArrayOf(a) to longArrayOf(b) } }+List(120) { LongArray(random.nextInt(0,12)) { weights.random(random) } to LongArray(random.nextInt(0,12)) { weights.random(random) } }
        for ((a,b) in pairs) {
            val ad=compareFixtureData(a); val bd=compareFixtureData(b); val sad=bridge.exportTarget(ad); val sbd=bridge.exportTarget(bd)
            for (strength in listOf(0,1,2,3,15)) for (flags in listOf(0,4,0x800,0x804,0x200,0x300,0x400,0x500,0x700)) {
                val settings=CollationSettings().apply { options=(strength shl 12) or flags; variableTop=0x20000000 }
                compare(SourceUTF16(sad,false,if(a.isEmpty()) "" else "x",0),SourceUTF16(sbd,false,if(b.isEmpty()) "" else "x",0),compareFixtureIterator(a),compareFixtureIterator(b),settings,"synthetic/${a.toList()}/${b.toList()}/$strength/$flags")
            }
        }
    }
    @Test fun nullsFailuresAndAliasedIteratorsRetainCallOrder() {
        val d=iteratorFixtureData(); val sd=bridge.exportTarget(d)
        for (leftPresent in listOf(false,true)) for (rightPresent in listOf(false,true)) for (settingsPresent in listOf(false,true)) {
            compare(if(leftPresent) SourceUTF16(sd,false,"abc",0) else null,if(rightPresent) SourceUTF16(sd,false,"xyz",0) else null,if(leftPresent) UTF16CollationIterator(d,false,"abc",0) else null,if(rightPresent) UTF16CollationIterator(d,false,"xyz",0) else null,if(settingsPresent) CollationSettings() else null)
        }
        val s=SourceUTF16(sd,false,"abc",0); val t=UTF16CollationIterator(d,false,"abc",0)
        compare(s,s,t,t,CollationSettings())
        for (p in listOf(-1,4,Int.MAX_VALUE)) compare(SourceUTF16(sd,false,"abc",p),SourceUTF16(sd,false,"abc",0),UTF16CollationIterator(d,false,"abc",p),UTF16CollationIterator(d,false,"abc",0),CollationSettings())
    }
}
