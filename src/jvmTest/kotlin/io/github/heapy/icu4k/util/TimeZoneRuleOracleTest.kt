// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import com.ibm.icu.util.AnnualTimeZoneRule as SourceAnnual
import com.ibm.icu.util.DateTimeRule as SourceDate
import com.ibm.icu.util.InitialTimeZoneRule as SourceInitial
import com.ibm.icu.util.TimeArrayTimeZoneRule as SourceArray
import com.ibm.icu.util.TimeZoneRule as SourceRule
import com.ibm.icu.util.TimeZoneTransition as SourceTransition
import java.io.*
import kotlin.random.Random
import kotlin.test.*

class TimeZoneRuleOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block().let { if(it is java.util.Date) it.time else it } } catch(e: Throwable) {
        when(e) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> e.javaClass.simpleName+":"+e.message }
    }
    private fun state(a: SourceRule,b: TimeZoneRule) {
        assertEquals(listOf(a.name,a.rawOffset,a.dstSavings,a.isTransitionRule),listOf(b.getName(),b.getRawOffset(),b.getDSTSavings(),b.isTransitionRule()))
        assertEquals(outcome { a.toString() },outcome { b.toString() })
    }
    private fun dates(a: SourceRule,b: TimeZoneRule,base: Long,raw: Int,dst: Int) {
        assertEquals(outcome { a.getFirstStart(raw,dst) },outcome { b.getFirstStart(raw,dst) },"first/$base/$raw/$dst")
        assertEquals(outcome { a.getFinalStart(raw,dst) },outcome { b.getFinalStart(raw,dst) },"final/$base/$raw/$dst")
        for(inclusive in listOf(false,true)) {
            assertEquals(outcome { a.getNextStart(base,raw,dst,inclusive) },outcome { b.getNextStart(base,raw,dst,inclusive) },"next/$base/$raw/$dst/$inclusive")
            assertEquals(outcome { a.getPreviousStart(base,raw,dst,inclusive) },outcome { b.getPreviousStart(base,raw,dst,inclusive) },"previous/$base/$raw/$dst/$inclusive")
        }
    }
    @Test fun annualRulesRandomDatesYearsOffsetsAndInvalidInputs() {
        val random=Random(78137)
        val yearEdges=listOf(Int.MIN_VALUE,-2_000_000,-400,-1,0,1,100,400,1582,1900,2000,2023,2024,2100,2_000_000,Int.MAX_VALUE)
        repeat(2400) { i ->
            val month=if(i%10==0) random.nextInt(-2,14) else random.nextInt(12)
            val dom=random.nextInt(-5,35); val week=if(i%20==0) random.nextInt() else random.nextInt(-6,7)
            val dow=random.nextInt(0,9); val millis=random.nextInt(); val type=random.nextInt(-1,4); val after=random.nextBoolean()
            val ar: SourceDate; val br: DateTimeRule
            when(i%3) {
                0 -> { ar=SourceDate(month,dom,millis,type); br=DateTimeRule(month,dom,millis,type) }
                1 -> { ar=SourceDate(month,week,dow,millis,type); br=DateTimeRule(month,week,dow,millis,type) }
                else -> { ar=SourceDate(month,dom,dow,after,millis,type); br=DateTimeRule(month,dom,dow,after,millis,type) }
            }
            val start=if(i%3==0) yearEdges.random(random) else random.nextInt(-2000,3000)
            val end=if(i%4==0) Int.MAX_VALUE else if(i%5==0) start-1 else start+random.nextInt(0,3000)
            val a=SourceAnnual(if(i%7==0) null else "rule",random.nextInt(),random.nextInt(),ar,start,end)
            val b=AnnualTimeZoneRule(a.name,a.rawOffset,a.dstSavings,br,start,end)
            state(a,b); assertSame(br,b.getRule()); assertEquals(a.startYear,b.getStartYear()); assertEquals(a.endYear,b.getEndYear())
            val raw=random.nextInt(); val dst=random.nextInt()
            for(year in listOf(start-1,start,start+1,end,end+1,yearEdges.random(random))) assertEquals(outcome { a.getStartInYear(year,raw,dst) },outcome { b.getStartInYear(year,raw,dst) },"$i/$year")
            val base=if(i%4==0) random.nextLong() else random.nextLong(-100_000_000_000_000L,100_000_000_000_000L)
            dates(a,b,base,raw,dst)
            val boundary=outcome { a.getStartInYear(start,raw,dst) }
            if(boundary is Long) for(t in listOf(boundary-1,boundary,boundary+1)) dates(a,b,t,raw,dst)
        }
    }
    @Test fun arraysOverflowDuplicatesOffsetTypesAndDefensiveCopies() {
        val random=Random(78138)
        repeat(1400) { i ->
            val times=LongArray(random.nextInt(1,15)) { if(i%2==0) random.nextLong() else random.nextLong(-5000,5000) }
            if(i%4==0) times[0]=Long.MIN_VALUE
            if(i%5==0) times[times.lastIndex]=Long.MAX_VALUE
            val type=random.nextInt(-1,5); val raw=random.nextInt(); val dst=random.nextInt()
            val a=SourceArray("array",raw,dst,times,type); val b=TimeArrayTimeZoneRule("array",raw,dst,times,type)
            state(a,b); assertContentEquals(a.startTimes,b.getStartTimes()); assertEquals(a.timeType,b.getTimeType())
            times[0]=17; a.startTimes[0]=99; b.getStartTimes()[0]=99
            assertContentEquals(a.startTimes,b.getStartTimes())
            for(base in listOf(Long.MIN_VALUE,Long.MAX_VALUE,0,random.nextLong(),a.getFirstStart(raw,dst).time,a.getFinalStart(raw,dst).time)) dates(a,b,base,raw,dst)
            val ad=a.getFirstStart(raw,dst); val bd=b.getFirstStart(raw,dst)!!
            ad.time=123; bd.setTime(123)
            assertEquals(a.getFirstStart(raw,dst).time,b.getFirstStart(raw,dst)!!.getTime())
        }
        for(times in listOf<LongArray?>(null,longArrayOf())) assertEquals(outcome { SourceArray(null,0,0,times,0); Unit },outcome { TimeArrayTimeZoneRule(null,0,0,times,0); Unit })
    }
    @Test fun equivalenceIdentityNullsAndSerialization() {
        val ad=SourceDate(2,14,0,0); val bd=DateTimeRule(2,14,0,0)
        val aa=listOf<SourceRule?>(null,SourceInitial(null,1,2),SourceInitial("other",1,2),SourceInitial("other",1,3),SourceAnnual("annual",1,2,ad,2000,2010),SourceAnnual("other",1,2,ad,2000,2010),SourceAnnual("different",1,2,SourceDate(2,14,0,0),2000,2010),SourceAnnual("null",1,2,null,2000,2010),SourceArray("array",1,2,longArrayOf(3,1,1),0),SourceArray("other",1,2,longArrayOf(1,3,1),0),SourceArray("other",1,2,longArrayOf(1,3),0))
        val bb=listOf<TimeZoneRule?>(null,InitialTimeZoneRule(null,1,2),InitialTimeZoneRule("other",1,2),InitialTimeZoneRule("other",1,3),AnnualTimeZoneRule("annual",1,2,bd,2000,2010),AnnualTimeZoneRule("other",1,2,bd,2000,2010),AnnualTimeZoneRule("different",1,2,DateTimeRule(2,14,0,0),2000,2010),AnnualTimeZoneRule("null",1,2,null,2000,2010),TimeArrayTimeZoneRule("array",1,2,longArrayOf(3,1,1),0),TimeArrayTimeZoneRule("other",1,2,longArrayOf(1,3,1),0),TimeArrayTimeZoneRule("other",1,2,longArrayOf(1,3),0))
        for(i in 1 until aa.size) {
            val a=aa[i]!!; val b=bb[i]!!; state(a,b); dates(a,b,0,0,0)
            for(j in aa.indices) assertEquals(outcome { a.isEquivalentTo(aa[j]) },outcome { b.isEquivalentTo(bb[j]) },"$i/$j")
            val bytes=ByteArrayOutputStream(); ObjectOutputStream(bytes).use { it.writeObject(b) }
            val copy=ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() } as TimeZoneRule
            state(a,copy); dates(a,copy,0,100,200)
            assertEquals(ObjectStreamClass.lookup(a.javaClass).serialVersionUID,ObjectStreamClass.lookup(b.javaClass).serialVersionUID)
        }
        for(i in aa.indices) {
            val a=SourceTransition(Long.MIN_VALUE,aa[i],aa[aa.lastIndex-i]); val b=TimeZoneTransition(Long.MIN_VALUE,bb[i],bb[bb.lastIndex-i])
            assertEquals(a.time,b.getTime()); assertSame(bb[i],b.getFrom()); assertSame(bb[bb.lastIndex-i],b.getTo())
            assertEquals(outcome { a.toString() },outcome { b.toString() })
        }
    }
    @Test fun virtualAnnualStartDispatchAndPrivateBaseFields() {
        var ac=0; var bc=0
        val a=object : SourceAnnual("virtual",1,2,SourceDate(0,1,0,2),2000,3000) {
            override fun getStartInYear(year: Int,raw: Int,dst: Int): java.util.Date? { ac++; return java.util.Date(if(ac%2==0) 0 else -1) }
            override fun getName(): String = "overridden"
            override fun getRawOffset(): Int = 999
        }
        val b=object : AnnualTimeZoneRule("virtual",1,2,DateTimeRule(0,1,0,2),2000,3000) {
            override fun getStartInYear(year: Int,prevRawOffset: Int,prevDSTSavings: Int): Date? { bc++; return Date(if(bc%2==0) 0 else -1) }
            override fun getName(): String = "overridden"
            override fun getRawOffset(): Int = 999
        }
        dates(a,b,946684800000L,0,0); assertEquals(ac,bc); state(a,b)
        assertEquals(a.isEquivalentTo(SourceAnnual("other",1,2,a.rule,2000,3000)),b.isEquivalentTo(AnnualTimeZoneRule("other",1,2,b.getRule(),2000,3000)))
        val date: java.util.Date=b.getFirstStart(0,0)!! // JVM boundary is the original host type.
        assertTrue(date.time <= 0)
    }
}
