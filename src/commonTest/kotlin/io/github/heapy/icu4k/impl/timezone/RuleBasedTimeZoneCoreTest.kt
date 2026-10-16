// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.util.*
import kotlin.test.*

class RuleBasedTimeZoneCoreTest {
    private fun zone(): RuleBasedTimeZoneCore = RuleBasedTimeZoneCore(InitialTimeZoneRule("STD",-18_000_000,0)).also {
        it.addTransitionRule(AnnualTimeZoneRule("DST",-18_000_000,3_600_000,DateTimeRule(2,2,1,7_200_000,0),2007,Int.MAX_VALUE))
        it.addTransitionRule(AnnualTimeZoneRule("STD",-18_000_000,0,DateTimeRule(10,1,1,7_200_000,0),2007,Int.MAX_VALUE))
    }
    @Test fun recurringOffsetsTransitionsAndLocalOptions() {
        val z=zone(); val offsets=IntArray(2)
        z.getOffset(1710053999999L,false,offsets); assertContentEquals(intArrayOf(-18_000_000,0),offsets)
        z.getOffset(1710054000000L,false,offsets); assertContentEquals(intArrayOf(-18_000_000,3_600_000),offsets)
        assertEquals(1710054000000L,z.getNextTransition(1704067200000L,false)!!.getTime())
        assertEquals(1710054000000L,z.getPreviousTransition(1710054000000L,true)!!.getTime())
        // 2024-03-10 02:30 wall time is in the spring gap.
        z.getOffsetFromLocal(1710037800000L,4,12,offsets); assertEquals(0,offsets[1])
        z.getOffsetFromLocal(1710037800000L,12,12,offsets); assertEquals(3_600_000,offsets[1])
        assertEquals(-14_400_000,z.getOffset(1,2024,2,10,1,9_000_000))
    }
    @Test fun basicAlgorithmsOperateOnTheCommonTransitionEngine() {
        val engine=zone()
        val basic=object : BasicTimeZoneCore() {
            override fun getID(): String = "common"
            override fun getOffset(date: Long,local: Boolean,offsets: IntArray) = engine.getOffset(date,local,offsets)
            override fun getNextTransition(base: Long,inclusive: Boolean): TimeZoneTransition? = engine.getNextTransition(base,inclusive)
            override fun getPreviousTransition(base: Long,inclusive: Boolean): TimeZoneTransition? = engine.getPreviousTransition(base,inclusive)
            override fun getTimeZoneRules(): Array<TimeZoneRule?> = engine.getTimeZoneRules()
        }
        val rules=basic.getSimpleTimeZoneRulesNear(1704067200000L)
        assertEquals(3,rules.size)
        assertEquals("DST",rules[0]!!.getName())
        val filtered=basic.getTimeZoneRules(1704067200000L)!!
        assertEquals(3,filtered.size); assertIs<InitialTimeZoneRule>(filtered[0])
        assertTrue(filtered.drop(1).all { (it as AnnualTimeZoneRule).getStartYear()==2024 })
    }
    @Test fun dirtyRebuildAndClonesRetainSharedHistoricCache() {
        val initial=InitialTimeZoneRule("initial",0,0)
        val z=RuleBasedTimeZoneCore(initial)
        z.addTransitionRule(TimeArrayTimeZoneRule("one",1,0,longArrayOf(100),2))
        assertEquals(100,z.getNextTransition(0,false)!!.getTime())
        val cache=z.historicTransitions!!; assertEquals(1,cache.size)
        val copy=z.cloneAsThawed()
        assertSame(cache,copy.historicTransitions); assertNotSame(z.historicRules,copy.historicRules)
        assertSame(z.getTimeZoneRules()[1],copy.getTimeZoneRules()[1])
        copy.addTransitionRule(TimeArrayTimeZoneRule("two",2,0,longArrayOf(200),2))
        assertFalse(copy.upToDate); copy.getOffset(300,false,IntArray(2))
        assertEquals(3,cache.size); assertTrue(z.upToDate)
        z.freeze(); assertSame(z,z.clone()); assertFalse(z.cloneAsThawed().isFrozen())
        assertFailsWith<UnsupportedOperationException> { z.addTransitionRule(null) }
    }
    @Test fun incompleteRulesValidationAndPartialOutput() {
        val z=RuleBasedTimeZoneCore(InitialTimeZoneRule("initial",123,456))
        val output=intArrayOf(99)
        assertFailsWith<IndexOutOfBoundsException> { z.getOffset(0,false,output) }
        assertEquals(123,output[0])
        assertFailsWith<IllegalArgumentException> { z.addTransitionRule(InitialTimeZoneRule("bad",0,0)) }
        z.addTransitionRule(AnnualTimeZoneRule("final",0,0,DateTimeRule(0,1,0,2),2000,Int.MAX_VALUE))
        assertEquals(2,z.getTimeZoneRules().size)
        assertEquals("Incomplete final rules",assertFailsWith<IllegalStateException> { z.freeze() }.message)
        assertFalse(z.isFrozen()); assertFalse(z.upToDate)
        assertFailsWith<UnsupportedOperationException> { z.setRawOffset(0) }
    }
    @Test fun localDeltaOverflowAndVirtualQueries() {
        assertEquals(Int.MIN_VALUE,RuleBasedTimeZoneCore.getLocalDelta(Int.MAX_VALUE,1,Int.MIN_VALUE,0,12,4))
        val z=RuleBasedTimeZoneCore(null) { 1234L }
        var seen=0L
        assertEquals(77,z.getRawOffset { t,local,offsets -> seen=t; assertFalse(local); offsets!![0]=77 })
        assertEquals(1234L,seen)
        assertTrue(z.useDaylightTime({ _,_,a -> a!![1]=1 },{ _,_ -> error("not queried") }))
        val transition=TimeZoneTransition(2000,InitialTimeZoneRule("a",0,0),InitialTimeZoneRule("b",0,1))
        assertTrue(z.observesDaylightTime({ _,_,_ -> },{ t,_ -> assertEquals(1234L,t); transition }))
    }
}
