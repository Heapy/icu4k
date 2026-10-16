// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.timezone

import com.ibm.icu.util.BasicTimeZone as Source
import com.ibm.icu.util.TimeZoneRule as SRule
import com.ibm.icu.util.InitialTimeZoneRule as SInitial
import com.ibm.icu.util.AnnualTimeZoneRule as SAnnual
import com.ibm.icu.util.TimeArrayTimeZoneRule as SArray
import com.ibm.icu.util.DateTimeRule as SDate
import com.ibm.icu.util.TimeZoneTransition as STransition
import com.ibm.icu.util.RuleBasedTimeZone as SZone
import io.github.heapy.icu4k.util.*
import java.util.IdentityHashMap
import kotlin.random.Random
import kotlin.test.*

class BasicTimeZoneCoreOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        when(e) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> e.javaClass.simpleName+":"+e.message }
    }
    private class Mapper {
        val rules = IdentityHashMap<SRule,TimeZoneRule>()
        private val dates = IdentityHashMap<SDate,DateTimeRule>()
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
                else -> object : TimeZoneRule(it.name,it.rawOffset,it.dstSavings) {
                    override fun getFirstStart(prevRawOffset: Int,prevDSTSavings: Int): Date? = it.getFirstStart(prevRawOffset,prevDSTSavings)
                    override fun getFinalStart(prevRawOffset: Int,prevDSTSavings: Int): Date? = it.getFinalStart(prevRawOffset,prevDSTSavings)
                    override fun getNextStart(base: Long,prevRawOffset: Int,prevDSTSavings: Int,inclusive: Boolean): Date? = it.getNextStart(base,prevRawOffset,prevDSTSavings,inclusive)
                    override fun getPreviousStart(base: Long,prevRawOffset: Int,prevDSTSavings: Int,inclusive: Boolean): Date? = it.getPreviousStart(base,prevRawOffset,prevDSTSavings,inclusive)
                    override fun isTransitionRule(): Boolean = it.isTransitionRule
                }
            }
        } }
        fun transition(t: STransition?): TimeZoneTransition? = t?.let { TimeZoneTransition(it.time,rule(it.from),rule(it.to)) }
    }
    private class Adapter(private val source: Source) : BasicTimeZoneCore() {
        val mapper=Mapper()
        override fun getID(): String? = source.id
        override fun getOffset(date: Long,local: Boolean,offsets: IntArray) = source.getOffset(date,local,offsets)
        override fun getNextTransition(base: Long,inclusive: Boolean): TimeZoneTransition? = mapper.transition(source.getNextTransition(base,inclusive))
        override fun getPreviousTransition(base: Long,inclusive: Boolean): TimeZoneTransition? = mapper.transition(source.getPreviousTransition(base,inclusive))
        override fun getTimeZoneRules(): Array<TimeZoneRule?>? = source.timeZoneRules?.map { mapper.rule(it) }?.toTypedArray()
    }
    private class Spy(val rules: Array<SRule>?,val initial: SRule,val events: List<STransition>) : Source("fixture") {
        val trace=ArrayList<String>()
        override fun getOffset(date: Long,local: Boolean,offsets: IntArray) {
            trace.add("offset/$date/$local")
            val r=events.lastOrNull { it.time<=date }?.to ?: initial
            offsets[0]=r.rawOffset; offsets[1]=r.dstSavings
        }
        override fun getNextTransition(base: Long,inclusive: Boolean): STransition? {
            trace.add("next/$base/$inclusive")
            return events.firstOrNull { it.time>base || inclusive && it.time==base }
        }
        override fun getPreviousTransition(base: Long,inclusive: Boolean): STransition? {
            trace.add("previous/$base/$inclusive")
            return events.lastOrNull { it.time<base || inclusive && it.time==base }
        }
        override fun getTimeZoneRules(): Array<SRule>? { trace.add("rules"); return rules }
        override fun getRawOffset(): Int = initial.rawOffset
        override fun setRawOffset(offsetMillis: Int) { throw UnsupportedOperationException() }
        override fun useDaylightTime(): Boolean = false
        override fun inDaylightTime(date: java.util.Date): Boolean = false
        override fun getOffset(era: Int,year: Int,month: Int,day: Int,dayOfWeek: Int,milliseconds: Int): Int = throw UnsupportedOperationException()
    }
    private fun sRule(r: SRule?): Any? = r?.let { listOf(it.name,it.rawOffset,it.dstSavings,it.toString(),when(it) {
        is SAnnual -> listOf(it.startYear,it.endYear,it.rule?.let { d -> listOf(d.dateRuleType,d.ruleMonth,d.ruleDayOfMonth,d.ruleDayOfWeek,d.ruleWeekInMonth,d.ruleMillisInDay,d.timeRuleType) })
        is SArray -> listOf(it.timeType,it.startTimes.toList())
        else -> null
    }) }
    private fun tRule(r: TimeZoneRule?): Any? = r?.let { listOf(it.getName(),it.getRawOffset(),it.getDSTSavings(),it.toString(),when(it) {
        is AnnualTimeZoneRule -> listOf(it.getStartYear(),it.getEndYear(),it.getRule()?.let { d -> listOf(d.getDateRuleType(),d.getRuleMonth(),d.getRuleDayOfMonth(),d.getRuleDayOfWeek(),d.getRuleWeekInMonth(),d.getRuleMillisInDay(),d.getTimeRuleType()) })
        is TimeArrayTimeZoneRule -> listOf(it.getTimeType(),it.getStartTimes().toList())
        else -> null
    }) }
    private fun result(source: () -> Array<SRule>?,target: () -> Array<TimeZoneRule?>?) {
        assertEquals(outcome { source()?.map(::sRule) },outcome { target()?.map(::tRule) })
    }
    private fun zone(seed: Int): SZone {
        val initial=SInitial("initial",(seed%5-2)*3_600_000,0)
        val zone=SZone("zone",initial)
        for(i in 0 until 4) zone.addTransitionRule(SArray("history$i",(seed%5-2)*3_600_000,(i%2)*3_600_000,longArrayOf(-20_000_000_000L+i*2_000_000_000L, i*2_000_000_000L),seed%3))
        zone.addTransitionRule(SAnnual("DST",(seed%5-2)*3_600_000,3_600_000,SDate(2,2,1,7_200_000,seed%3),2000,Int.MAX_VALUE))
        zone.addTransitionRule(SAnnual("STD",(seed%5-2)*3_600_000,0,SDate(10,1,1,7_200_000,seed%3),2000,Int.MAX_VALUE))
        return zone
    }
    @Test fun realRuleBasedZonesFilteredAndSimplifiedRules() {
        val random=Random(78139)
        repeat(18) { seed ->
            val a=zone(seed); val b=Adapter(zone(seed))
            val dates=mutableListOf(Long.MIN_VALUE,Long.MAX_VALUE,-30_000_000_000L,0,946684800000L,1704067200000L)
            repeat(30) { dates.add(random.nextLong(-50_000_000_000L,3_000_000_000_000L)) }
            for(date in dates) {
                result({ a.getTimeZoneRules(date) },{ b.getTimeZoneRules(date) })
                result({ a.getSimpleTimeZoneRulesNear(date) },{ b.getSimpleTimeZoneRulesNear(date) })
            }
        }
    }
    @Test fun finiteHistoriesTraceAliasingMalformedRulesAndArrayTimeTypes() {
        val random=Random(78140)
        repeat(350) { seed ->
            val initial=SInitial("initial",random.nextInt(-5000,5000),random.nextInt(-2,3))
            val r1=SArray("array",random.nextInt(-5000,5000),random.nextInt(-2,3),longArrayOf(-10000,-100,0,100,1000,10000),seed%5-1)
            val r2=SAnnual("annual",random.nextInt(-5000,5000),seed%2,SDate(0,1,0,2),1960,if(seed%3==0) Int.MAX_VALUE else 2000)
            val all=if(seed%17==0) arrayOf<SRule>(initial) else arrayOf<SRule>(initial,r1,r2)
            val events=listOf(STransition(-5000,initial,r1),STransition(-100,r1,r2),STransition(100,r2,r1),STransition(5000,r1,r2))
            val a=Spy(all,initial,events); val feed=Spy(all,initial,events); val b=Adapter(feed)
            for(date in listOf(-10001L,-5000,-4999,-101,-100,-99,0,100,101,4999,5000,5001)) {
                a.trace.clear(); feed.trace.clear()
                result({ a.getTimeZoneRules(date) },{ b.getTimeZoneRules(date) })
                assertEquals(a.trace,feed.trace,"$seed/$date/filter")
                a.trace.clear(); feed.trace.clear()
                result({ a.getSimpleTimeZoneRulesNear(date) },{ b.getSimpleTimeZoneRulesNear(date) })
                assertEquals(a.trace,feed.trace,"$seed/$date/simple")
            }
        }
        val initial=SInitial("initial",0,0); val rules=arrayOf<SRule>(initial)
        val source=Spy(rules,initial,emptyList())
        assertSame(rules,source.getTimeZoneRules(0))
        val nullSource=Spy(null,initial,emptyList()); val nullTarget=Adapter(nullSource)
        assertNull(nullSource.getTimeZoneRules(0)); assertNull(nullTarget.getTimeZoneRules(0))
    }
    @Test fun nullRuleArrayFailsBeforeReadingPreviousTransitionAttributes() {
        val namesA=ArrayList<String>(); val namesB=ArrayList<String>()
        val initial=SInitial("initial",0,0)
        val toA=object : SInitial("to",0,0) { override fun getName(): String { namesA.add("name"); return "to" } }
        val toB=object : InitialTimeZoneRule("to",0,0) { override fun getName(): String { namesB.add("name"); return "to" } }
        val a=Spy(null,initial,listOf(STransition(0,initial,toA)))
        val b=object : BasicTimeZoneCore() {
            override fun getID(): String = "fixture"
            override fun getOffset(date: Long,local: Boolean,offsets: IntArray) { error("unused") }
            override fun getNextTransition(base: Long,inclusive: Boolean): TimeZoneTransition? = null
            override fun getPreviousTransition(base: Long,inclusive: Boolean): TimeZoneTransition = TimeZoneTransition(0,null,toB)
            override fun getTimeZoneRules(): Array<TimeZoneRule?>? = null
        }
        assertEquals(outcome { a.getTimeZoneRules(0) },outcome { b.getTimeZoneRules(0) })
        assertEquals(emptyList(),namesA); assertEquals(namesA,namesB)
    }
    @Test fun equivalenceExactAndIgnoringDstAmountsWithVirtualTrace() {
        val random=Random(78141)
        repeat(900) { seed ->
            val ar=(0 until 5).map { SInitial("r$it",random.nextInt(-3,4),random.nextInt(-1,3)) }
            val br=ar.mapIndexed { i,r -> if(seed%3==0) SInitial("b$i",r.rawOffset,r.dstSavings) else SInitial("b$i",random.nextInt(-3,4),random.nextInt(-1,3)) }
            val times=listOf(-100L,0,100,200)
            val ae=times.mapIndexed { i,t -> STransition(t,ar[i],ar[i+1]) }
            val be=times.mapIndexed { i,t -> STransition(t+if(seed%7==0) 1 else 0,br[i],br[i+1]) }
            val a=Spy(ar.toTypedArray(),ar[0],ae); val other=Spy(br.toTypedArray(),br[0],be)
            val feedA=Spy(ar.toTypedArray(),ar[0],ae); val feedB=Spy(br.toTypedArray(),br[0],be)
            val b=Adapter(feedA); val otherB=Adapter(feedB)
            val start=listOf(-101L,-100,-1,0,1,99,100,200,201).random(random); val end=listOf(-101L,0,100,199,200,201).random(random)
            for(ignore in listOf(false,true)) {
                a.trace.clear(); other.trace.clear(); feedA.trace.clear(); feedB.trace.clear()
                assertEquals(a.hasEquivalentTransitions(other,start,end,ignore),b.hasEquivalentTransitions(otherB,start,end,ignore),"$seed/$start/$end/$ignore")
                assertEquals(a.trace,feedA.trace); assertEquals(other.trace,feedB.trace)
            }
            assertTrue(b.hasEquivalentTransitions(b,start,end)); assertFalse(b.hasEquivalentTransitions(null,start,end))
        }
    }
}
