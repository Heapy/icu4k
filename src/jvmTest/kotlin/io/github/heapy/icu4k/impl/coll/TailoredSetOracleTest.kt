// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import com.ibm.icu.impl.coll.TailoredSet as Source
import com.ibm.icu.impl.coll.CollationData as SourceData
import com.ibm.icu.text.UnicodeSet as SourceSet
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.random.Random
import kotlin.test.*

class TailoredSetOracleTest {
    private fun field(a: Any,name: String): Any? = a.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(a)
    private fun outcome(block: () -> Unit): Any? = try { block();null } catch(e: Throwable) {
        when(e) { is NullPointerException -> "null";is IndexOutOfBoundsException -> "bounds";else -> e.javaClass.simpleName+":"+e.message }
    }
    private fun compare(sd: SourceData?,td: CollationData?,frozen: Boolean=false,present: Boolean=true,reuse: Boolean=true) {
        val a=if(present) SourceSet().apply { if(frozen) freeze() } else null
        val b=if(present) UnicodeSet().apply { if(frozen) freeze() } else null
        val source=Source(a);val target=TailoredSet(b)
        repeat(if(reuse) 2 else 1) {
            assertEquals(outcome { source.forData(sd) },outcome { target.forData(td) })
            assertEquals(a?.toPattern(false),b?.toPattern(false))
            assertEquals(field(source,"suffix"),field(target,"suffix"))
            assertEquals(field(source,"unreversedPrefix").toString(),field(target,"unreversedPrefix").toString())
            val sourceData=field(source,"data");val targetData=field(target,"data")
            assertEquals(sourceData===sd,targetData===td)
            assertEquals(field(source,"baseData")===sd?.base,field(target,"baseData")===td?.base)
        }
    }
    @Test fun everyLocaleTailoringAndRuleBuiltContexts() {
        val field=com.ibm.icu.text.RuleBasedCollator::class.java.getDeclaredField("data").apply { isAccessible=true }
        val index=com.ibm.icu.util.UResourceBundle.getBundleInstance(com.ibm.icu.impl.ICUData.ICU_COLLATION_BASE_NAME,"res_index")
        val names=index.get("InstalledLocales").keySet().sorted()
        val sources=names.map { field.get(com.ibm.icu.text.Collator.getInstance(com.ibm.icu.util.ULocale(it))) as SourceData }+
            listOf("&a < x|b < xy|b < x|bc / d","&a < 😀|b < 😀|bc / e","&a < ch < cH < Ch < CH","&[before 1]b < ä / e [optimize [a-z]]","&a < ᄀ").map { field.get(com.ibm.icu.text.RuleBasedCollator(it)) as SourceData }
        for(sd in sources) {
            val td=CollationDataOracleTest().importSource(sd)
            compare(sd,td);compare(sd,td,frozen=true);compare(sd,td,present=false)
        }
    }
    @Test fun syntheticMappingsOffsetExpansionsAndMalformedMetadata() {
        val random=Random(78319);val bridge=CollationDataOracleTest()
        val ce32s=listOf(iteratorSimple(65),iteratorSimple(66),0x123456c1,0x123456c2,0x231234c4,Collation.makeCE32FromTagIndexAndLength(5,0,2),Collation.makeCE32FromTagIndexAndLength(6,0,2),Collation.makeCE32FromTagAndIndex(14,2),Collation.UNASSIGNED_CE32,Collation.makeCE32FromTagAndIndex(10,0),0xcb,0xcc)
        for(i in 0 until 300) {
            val mappings=(0..12).associateWith { ce32s.random(random) }
            val base=iteratorFixtureData().apply { trie=tailoredFixtureTrie(mappings) }
            val child=iteratorFixtureData().apply {
                this.base=base;trie=tailoredFixtureTrie(mappings+mapOf(random.nextInt(13) to ce32s.random(random)))
                if(i%3==0) ces=ces!!.copyOf().also { it[random.nextInt(it.size)]++ }
                if(i%5==0) this.ce32s=this.ce32s!!.copyOf().also { it[random.nextInt(it.size)]=iteratorSimple(random.nextInt(100)) }
                if(i%13==0) this.ce32s=null
                if(i%17==0) this.ces=null
            }
            compare(bridge.exportTarget(child),child,reuse=i%2==0)
        }
        val data=iteratorFixtureData();compare(bridge.exportTarget(data),data);compare(null,null)
    }
}
