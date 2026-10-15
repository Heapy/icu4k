// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import com.ibm.icu.impl.coll.CollationWeights as Source
import kotlin.random.Random
import kotlin.test.*

class CollationWeightsOracleTest {
    private fun field(value: Any,name: String): Any? = value.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(value)
    private fun state(value: Any): List<Any?> = listOf(field(value,"middleLength"), (field(value,"minBytes") as IntArray).toList(),(field(value,"maxBytes") as IntArray).toList(),field(value,"rangeIndex"),field(value,"rangeCount"),(field(value,"ranges") as Array<*>).map { r -> r?.let { listOf(field(r,"start"),field(r,"end"),field(r,"length"),field(r,"count")) } })
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) { when(e) { is IndexOutOfBoundsException -> "bounds";is NullPointerException -> "null";else -> e.javaClass.simpleName+":"+e.message } }
    @Test fun allocationAndPartialConsumptionHistories() {
        val source=Source();val target=CollationWeights();val random=Random(78309)
        for(mode in 0..3) {
            when(mode) { 0 -> { source.initForPrimary(false);target.initForPrimary(false) };1 -> { source.initForPrimary(true);target.initForPrimary(true) };2 -> { source.initForSecondary();target.initForSecondary() };else -> { source.initForTertiary();target.initForTertiary() } }
            assertEquals(state(source),state(target))
            val limits=if(mode<2) listOf(0x03000000L,0x03040202L,0x10000000L,0x10020000L,0x10020200L,0x10020202L,0x10020203L,0x10feffffL,0x11000000L,0xfe000000L,0xff000000L,0xffffffffL)
                else listOf(0x0200L,0x0202L,0x0203L,0x023fL,0x02ffL,0x0300L,0x0500L,0x3f00L,0x3f3fL,0xff00L,0xffffL,0x10000L)
            val cases=limits.flatMap { lower -> limits.map { upper -> lower to upper } }+List(400) {
                if(mode<2) { val lower=random.nextLong(0x03000000L,0xff000000L); lower to random.nextLong(lower+1,0xffffffffL+1) }
                else { val lower=random.nextLong(0x200,0xffff); lower to random.nextLong(lower+1,0x10001) }
            }
            for((index,limitsPair) in cases.withIndex()) {
                val (lower,upper)=limitsPair
                val count=listOf(1,2,3,60,62,63,254,255,256,1000,4000)[index%11]
                assertEquals(outcome { source.allocWeights(lower,upper,count) },outcome { target.allocWeights(lower,upper,count) },"$mode/$lower/$upper/$count")
                assertEquals(state(source),state(target),"allocation state/$index")
                repeat(if(index%3==0) count+2 else 2) {
                    assertEquals(outcome { source.nextWeight() },outcome { target.nextWeight() },"next/$index/$it")
                }
                assertEquals(state(source),state(target),"iteration state/$index")
            }
        }
    }
    @Test fun allStaticAndPrivateArithmeticHelpers() {
        val random=Random(783)
        val companion=CollationWeights.Companion
        val methods=companion.javaClass.declaredMethods.associateBy { it.name }
        for(name in listOf("lengthOfWeight","getWeightTrail","setWeightTrail","getWeightByte","setWeightByte","truncateWeight","incWeightTrail","decWeightTrail")) {
            val source=Source::class.java.declaredMethods.single { it.name==name }.apply { isAccessible=true }
            val target=methods.getValue(name).apply { isAccessible=true }
            repeat(1000) {
                val weight=random.nextLong();val length=random.nextInt(1,5);val b=random.nextInt()
                val args=when(source.parameterCount) { 1 -> arrayOf<Any>(weight);2 -> arrayOf<Any>(weight,length);else -> arrayOf<Any>(weight,length,b) }
                assertEquals(source.invoke(null,*args),target.invoke(companion,*args),"$name/${args.toList()}")
            }
        }
    }
}
