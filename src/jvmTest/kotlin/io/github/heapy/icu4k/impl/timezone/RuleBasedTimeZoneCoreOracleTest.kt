// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION", "UNCHECKED_CAST")
package io.github.heapy.icu4k.impl.timezone

import com.ibm.icu.util.RuleBasedTimeZone as Source
import com.ibm.icu.util.TimeZoneRule as SRule
import com.ibm.icu.util.InitialTimeZoneRule as SInitial
import com.ibm.icu.util.AnnualTimeZoneRule as SAnnual
import com.ibm.icu.util.TimeArrayTimeZoneRule as SArray
import com.ibm.icu.util.DateTimeRule as SDate
import com.ibm.icu.util.BasicTimeZone.LocalOption as Option
import com.ibm.icu.util.TimeZoneTransition as STransition
import io.github.heapy.icu4k.util.*
import java.util.IdentityHashMap
import kotlin.random.Random
import kotlin.test.*

class RuleBasedTimeZoneCoreOracleTest {
    private fun field(a: Any,n: String): Any? = generateSequence(a.javaClass) { it.superclass }.flatMap { it.declaredFields.asSequence() }.first { it.name==n }.apply { isAccessible=true }.get(a)
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        when(e) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> e.javaClass.simpleName+":"+e.message }
    }
    private class Mapper {
        val rules=IdentityHashMap<SRule,TimeZoneRule>()
        private val dates=IdentityHashMap<SDate,DateTimeRule>()
        fun date(d: SDate?): DateTimeRule? = d?.let { dates.getOrPut(it) {
            when(it.dateRuleType) {
                0 -> DateTimeRule(it.ruleMonth,it.ruleDayOfMonth,it.ruleMillisInDay,it.timeRuleType)
                1 -> DateTimeRule(it.ruleMonth,it.ruleWeekInMonth,it.ruleDayOfWeek,it.ruleMillisInDay,it.timeRuleType)
                else -> DateTimeRule(it.ruleMonth,it.ruleDayOfMonth,it.ruleDayOfWeek,it.dateRuleType==2,it.ruleMillisInDay,it.timeRuleType)
            }
        } }
        fun rule(r: SRule?): TimeZoneRule? = r?.let { rules.getOrPut(it) {
            when(it) {
                is SInitial -> InitialTimeZoneRule(it.name,it.rawOffset,it.dstSavings)
                is SAnnual -> AnnualTimeZoneRule(it.name,it.rawOffset,it.dstSavings,date(it.rule),it.startYear,it.endYear)
                is SArray -> TimeArrayTimeZoneRule(it.name,it.rawOffset,it.dstSavings,it.startTimes,it.timeType)
                else -> error("Unexpected rule")
            }
        } }
    }
    private data class Pairing(var a: Source,var b: RuleBasedTimeZoneCore,val mapper: Mapper)
    private fun pair(initial: SInitial?,mapper: Mapper=Mapper()): Pairing {
        var stamp=0L
        val a=object : Source("test",initial) {
            override fun getOffset(date: Long,local: Boolean,offsets: IntArray?) { stamp=date; super.getOffset(date,local,offsets) }
        }
        return Pairing(a,RuleBasedTimeZoneCore(mapper.rule(initial) as InitialTimeZoneRule?) { stamp },mapper)
    }
    private fun add(p: Pairing,rule: SRule?) {
        assertEquals(outcome { p.a.addTransitionRule(rule); Unit },outcome { p.b.addTransitionRule(p.mapper.rule(rule)); Unit })
    }
    private fun st(t: STransition?): Any? = t?.let { listOf(it.time,it.from?.toString(),it.to?.toString()) }
    private fun tt(t: TimeZoneTransition?): Any? = t?.let { listOf(it.getTime(),it.getFrom()?.toString(),it.getTo()?.toString()) }
    private fun state(p: Pairing) {
        val a=p.a; val b=p.b
        assertEquals(a.isFrozen,b.isFrozen()); assertEquals(field(a,"upToDate"),b.upToDate)
        val rules=a.timeZoneRules; val target=b.getTimeZoneRules()
        assertEquals(rules.map { it?.toString() },target.map { it?.toString() })
        for(i in rules.indices) assertSame(p.mapper.rule(rules[i]),target[i])
        val hist=field(a,"historicRules") as List<SRule>?
        assertEquals(hist?.map { it.toString() },b.historicRules?.map { it.toString() })
        val finals=field(a,"finalRules") as Array<SAnnual?>?
        assertEquals(finals?.map { it?.toString() },b.finalRules?.map { it?.toString() })
        val transitions=field(a,"historicTransitions") as List<STransition>?
        assertEquals(transitions?.map(::st),b.historicTransitions?.map(::tt))
        if(transitions!=null) for(i in transitions.indices) {
            assertSame(p.mapper.rule(transitions[i].from),b.historicTransitions!![i].getFrom())
            assertSame(p.mapper.rule(transitions[i].to),b.historicTransitions!![i].getTo())
        }
    }
    private fun offsets(p: Pairing,t: Long,local: Boolean,size: Int=2) {
        val a=IntArray(size) { 177+it }; val b=a.copyOf()
        assertEquals(outcome { p.a.getOffset(t,local,a); Unit },outcome { p.b.getOffset(t,local,b); Unit },"$t/$local")
        assertContentEquals(a,b)
    }
    private fun transition(p: Pairing,t: Long,inclusive: Boolean) {
        assertEquals(outcome { st(p.a.getNextTransition(t,inclusive)) },outcome { tt(p.b.getNextTransition(t,inclusive)) },"next/$t/$inclusive")
        assertEquals(outcome { st(p.a.getPreviousTransition(t,inclusive)) },outcome { tt(p.b.getPreviousTransition(t,inclusive)) },"previous/$t/$inclusive")
    }
    private val flags=intArrayOf(4,12,5,13,7,15)
    @Test fun historicalFinalTransitionsUtcWallAndAllLocalOptions() {
        val random=Random(78142)
        repeat(65) { seed ->
            val raw=random.nextInt(-5,6)*3_600_000
            val p=pair(SInitial("initial",raw,0))
            repeat(seed%5) { i -> add(p,SArray("h$i",raw+(i%2)*60_000,(i%3-1)*1_800_000,LongArray(4) { random.nextLong(-100_000_000L,100_000_000L) },seed%5-1)) }
            if(seed%3!=0) {
                add(p,SAnnual("dst",raw,3_600_000,SDate(2,2,1,7_200_000,seed%3),2000,Int.MAX_VALUE))
                if(seed%7!=0) add(p,SAnnual("std",raw,0,SDate(10,1,1,7_200_000,seed%3),2000,Int.MAX_VALUE))
            }
            val dates=mutableListOf(Long.MIN_VALUE,Long.MAX_VALUE,-100_000_001L,0,100_000_001L,946684800000L,1710054000000L)
            var base=-200_000_000L
            repeat(5) {
                val tr=outcome { p.a.getNextTransition(base,false) }
                if(tr is STransition) {
                    for(t in listOf(tr.time,tr.time+tr.from.rawOffset+tr.from.dstSavings,tr.time+tr.to.rawOffset+tr.to.dstSavings)) dates.addAll(listOf(t-1,t,t+1))
                    base=tr.time
                }
            }
            for(t in dates.distinct()) {
                offsets(p,t,false); offsets(p,t,true); transition(p,t,false); transition(p,t,true)
                for(i in flags.indices) for(j in flags.indices) {
                    val a=IntArray(2); val b=IntArray(2)
                    assertEquals(outcome { p.a.getOffsetFromLocal(t,Option.values()[i],Option.values()[j],a); Unit },outcome { p.b.getOffsetFromLocal(t,flags[i],flags[j],b); Unit },"$seed/$t/$i/$j")
                    assertContentEquals(a,b)
                }
            }
            for(era in listOf(0,1,3)) assertEquals(outcome { p.a.getOffset(era,2024,2,10,99,9_000_000) },outcome { p.b.getOffset(era,2024,2,10,99,9_000_000) })
            val basic=object : BasicTimeZoneCore() {
                override fun getID(): String = "test"
                override fun getOffset(date: Long,local: Boolean,offsets: IntArray) = p.b.getOffset(date,local,offsets)
                override fun getNextTransition(base: Long,inclusive: Boolean): TimeZoneTransition? = p.b.getNextTransition(base,inclusive)
                override fun getPreviousTransition(base: Long,inclusive: Boolean): TimeZoneTransition? = p.b.getPreviousTransition(base,inclusive)
                override fun getTimeZoneRules(): Array<TimeZoneRule?> = p.b.getTimeZoneRules()
            }
            for(t in listOf(-200_000_000L,0L,1704067200000L)) {
                assertEquals(outcome { p.a.getTimeZoneRules(t).map { it.toString() } },outcome { basic.getTimeZoneRules(t)?.map { it.toString() } })
                assertEquals(outcome { p.a.getSimpleTimeZoneRulesNear(t).map { it.toString() } },outcome { basic.getSimpleTimeZoneRulesNear(t).map { it.toString() } })
            }
            assertEquals(outcome { p.a.rawOffset },outcome { p.b.getRawOffset() })
            assertEquals(outcome { p.a.useDaylightTime() },outcome { p.b.useDaylightTime() })
            assertEquals(outcome { p.a.observesDaylightTime() },outcome { p.b.observesDaylightTime() })
            state(p)
        }
    }
    @Test fun reusableHistoriesFreezeClonesAndSharedCaches() {
        val random=Random(78143)
        repeat(25) {
            val first=pair(SInitial("initial",0,0)); val pairs=mutableListOf(first)
            repeat(55) { step ->
                val p=pairs.random(random)
                when(random.nextInt(8)) {
                    0,1 -> add(p,SArray("r$step",random.nextInt(-5,6)*1000,random.nextInt(-1,2)*100,LongArray(3) { random.nextLong(-5000,5000) },2))
                    2 -> offsets(p,random.nextLong(-10000,10000),random.nextBoolean())
                    3 -> transition(p,random.nextLong(-10000,10000),random.nextBoolean())
                    4 -> {
                        val a=p.a.cloneAsThawed(); val b=p.b.cloneAsThawed()
                        assertEquals(field(p.a,"historicTransitions")===field(a,"historicTransitions"),p.b.historicTransitions===b.historicTransitions)
                        assertEquals(field(p.a,"historicRules")===field(a,"historicRules"),p.b.historicRules===b.historicRules)
                        if(pairs.size<6) pairs.add(Pairing(a,b,p.mapper))
                    }
                    5 -> assertEquals(outcome { p.a.freeze(); Unit },outcome { p.b.freeze(); Unit })
                    6 -> { assertEquals(p.a===p.a.clone(),p.b===p.b.clone()); assertEquals(p.a.hasSameRules(p.a),p.b.hasSameRules(p.b)) }
                    else -> {
                        val other=pairs.random(random)
                        assertEquals(p.a.hasSameRules(other.a),p.b.hasSameRules(other.b))
                    }
                }
                for(pair in pairs) state(pair)
            }
        }
    }
    @Test fun validationNullableInputsFailureMutationAndSameRuleQuirks() {
        for(initial in listOf<SInitial?>(null,SInitial(null,1,2),SInitial("initial",1,2))) {
            val p=pair(initial)
            add(p,null); add(p,SInitial("not a transition",0,0))
            offsets(p,0,false,0); offsets(p,0,false,1)
            assertEquals(outcome { p.a.getOffset(0,false,null); Unit },outcome { p.b.getOffset(0,false,null); Unit })
            state(p)
            add(p,SAnnual("final",0,0,SDate(0,1,0,2),2000,Int.MAX_VALUE))
            assertEquals(outcome { p.a.freeze(); Unit },outcome { p.b.freeze(); Unit }); state(p)
            add(p,SAnnual("final2",0,1,SDate(6,1,0,2),2000,Int.MAX_VALUE))
            offsets(p,0,false); state(p)
            add(p,SAnnual("final3",0,1,SDate(6,1,0,2),2000,Int.MAX_VALUE)); state(p)
            assertEquals(outcome { p.a.setRawOffset(123) },outcome { p.b.setRawOffset(123) })
        }
        val p=pair(SInitial("initial",0,0)); add(p,SArray(null,0,1,longArrayOf(1),2))
        offsets(p,0,false); state(p)
        val a=pair(SInitial("one",0,0)); val b=pair(SInitial("two",0,0))
        add(a,SArray("a",1,0,longArrayOf(1),2)); add(a,SArray("a",1,0,longArrayOf(1),2))
        add(b,SArray("different",1,0,longArrayOf(1),2)); add(b,SArray("other",2,0,longArrayOf(2),2))
        assertEquals(a.a.hasSameRules(b.a),a.b.hasSameRules(b.b))
    }
    @Test fun reentrantRuleAdditionIsSeenWithinTheCurrentBuildPass() {
        val p=pair(SInitial("initial",0,0))
        val extra=SArray("extra",2,0,longArrayOf(50),2)
        val targetExtra=p.mapper.rule(extra)!!
        val traceA=ArrayList<String>(); val traceB=ArrayList<String>()
        val ar=object : SArray("primary",1,0,longArrayOf(100),2) {
            var added=false
            override fun getNextStart(base: Long,raw: Int,dst: Int,inclusive: Boolean): java.util.Date? {
                traceA.add("$base/$raw/$dst/$inclusive")
                if(!added) { added=true; p.a.addTransitionRule(extra) }
                return super.getNextStart(base,raw,dst,inclusive)
            }
        }
        val br=object : TimeArrayTimeZoneRule("primary",1,0,longArrayOf(100),2) {
            var added=false
            override fun getNextStart(base: Long,prevRawOffset: Int,prevDSTSavings: Int,inclusive: Boolean): Date? {
                traceB.add("$base/$prevRawOffset/$prevDSTSavings/$inclusive")
                if(!added) { added=true; p.b.addTransitionRule(targetExtra) }
                return super.getNextStart(base,prevRawOffset,prevDSTSavings,inclusive)
            }
        }
        p.mapper.rules[ar]=br
        add(p,ar); transition(p,0,false)
        assertEquals(traceA,traceB); assertEquals(50,p.b.historicTransitions!![0].getTime()); state(p)
    }
    @Test fun ownerQueriesRecursiveNameSkippingAndFrozenOverride() {
        val traceA=ArrayList<String>(); val traceB=ArrayList<String>()
        val initial=SInitial("initial",0,0); val mapper=Mapper()
        val a=object : Source("test",initial) {
            override fun getNextTransition(base: Long,inclusive: Boolean): STransition? { traceA.add("next/$base/$inclusive"); return super.getNextTransition(base,inclusive) }
            override fun getPreviousTransition(base: Long,inclusive: Boolean): STransition? { traceA.add("previous/$base/$inclusive"); return super.getPreviousTransition(base,inclusive) }
            override fun isFrozen(): Boolean = false
        }
        val b=RuleBasedTimeZoneCore(mapper.rule(initial) as InitialTimeZoneRule)
        val one=SArray("rename",0,0,longArrayOf(100),2); val two=SArray("offset",1,0,longArrayOf(200),2)
        a.addTransitionRule(one); a.addTransitionRule(two); b.addTransitionRule(mapper.rule(one)); b.addTransitionRule(mapper.rule(two))
        fun next(t: Long,i: Boolean): TimeZoneTransition? { traceB.add("next/$t/$i"); return b.getNextTransition(t,i,::next) }
        fun previous(t: Long,i: Boolean): TimeZoneTransition? { traceB.add("previous/$t/$i"); return b.getPreviousTransition(t,i,::previous) }
        assertEquals(st(a.getNextTransition(0,false)),tt(next(0,false)))
        assertEquals(st(a.getPreviousTransition(150,false)),tt(previous(150,false))); assertEquals(traceA,traceB)
        a.freeze(); b.freeze()
        val more=SArray("after freeze",2,0,longArrayOf(300),2)
        assertEquals(outcome { a.addTransitionRule(more); Unit },outcome { b.addTransitionRule(mapper.rule(more),false); Unit })
        assertNotSame(a,a.clone()); assertNotSame(b,b.clone(false))
    }
    @Test fun currentTimeQueriesCanDispatchThroughOwnerOverrides() {
        val initial=SInitial("initial",0,0)
        val finals=arrayOf(SAnnual("f0",0,0,SDate(0,1,0,2),2000,Int.MAX_VALUE),SAnnual("f1",0,0,SDate(6,1,0,2),2000,Int.MAX_VALUE))
        val traceA=ArrayList<String>(); val traceB=ArrayList<String>(); var now=0L; var index=0
        val a=object : Source("test",initial) {
            override fun getOffset(date: Long,local: Boolean,offsets: IntArray?) { now=date; traceA.add("offset/$date/$local"); offsets!![0]=7; offsets[1]=0 }
            override fun getNextTransition(base: Long,inclusive: Boolean): STransition? { traceA.add("next/$base/$inclusive"); return STransition(base+1,initial,finals[index++]) }
        }
        val b=RuleBasedTimeZoneCore(null) { now }
        val mapper=Mapper()
        for(f in finals) { a.addTransitionRule(f); b.addTransitionRule(mapper.rule(f)) }
        var targetIndex=0
        val offset:(Long,Boolean,IntArray?)->Unit={ t,l,out -> traceB.add("offset/$t/$l"); out!![0]=7; out[1]=0 }
        val next:(Long,Boolean)->TimeZoneTransition?={ t,i -> traceB.add("next/$t/$i"); TimeZoneTransition(t+1,null,mapper.rule(finals[targetIndex++])) }
        assertEquals(a.rawOffset,b.getRawOffset(offset)); assertEquals(traceA,traceB)
        traceA.clear(); traceB.clear(); index=0; targetIndex=0
        assertEquals(a.useDaylightTime(),b.useDaylightTime(offset,next)); assertEquals(traceA,traceB)
        traceA.clear(); traceB.clear(); index=0; targetIndex=0
        assertEquals(a.observesDaylightTime(),b.observesDaylightTime(offset,next)); assertEquals(traceA,traceB)
        assertFalse(b.upToDate); assertNull(b.historicTransitions)
    }
    @Test fun localDeltaPrivateOracleFullWidthInputs() {
        val method=Source::class.java.getDeclaredMethod("getLocalDelta",*Array(6) { Int::class.javaPrimitiveType!! }).apply { isAccessible=true }
        val random=Random(78144)
        repeat(12000) {
            val a=random.nextInt(); val b=if(it%3==0) 0 else random.nextInt(); val c=random.nextInt(); val d=if(it%4==0) 0 else random.nextInt()
            val n=random.nextInt(); val dup=random.nextInt()
            assertEquals(method.invoke(null,a,b,c,d,n,dup),RuleBasedTimeZoneCore.getLocalDelta(a,b,c,d,n,dup))
        }
    }
}
