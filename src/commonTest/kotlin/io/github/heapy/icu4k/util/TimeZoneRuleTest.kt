// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.Grego
import kotlin.test.*

class TimeZoneRuleTest {
    @Test fun annualDstStartsInclusiveBoundariesAndLeapDayAdjustment() {
        val schedule = DateTimeRule(2,2,1,7_200_000,DateTimeRule.WALL_TIME)
        val annual = AnnualTimeZoneRule("DST",-18_000_000,3_600_000,schedule,2007,AnnualTimeZoneRule.MAX_YEAR)
        val t = 1710054000000L
        assertEquals(t,annual.getStartInYear(2024,-18_000_000,0)!!.getTime())
        assertEquals(t,annual.getNextStart(t,-18_000_000,0,true)!!.getTime())
        assertEquals(t,annual.getPreviousStart(t,-18_000_000,0,true)!!.getTime())
        assertTrue(annual.getNextStart(t,-18_000_000,0,false)!!.getTime() > t)
        assertTrue(annual.getPreviousStart(t,-18_000_000,0,false)!!.getTime() < t)
        assertNull(annual.getStartInYear(2006,0,0)); assertNull(annual.getFinalStart(0,0))
        val leap = AnnualTimeZoneRule("last Sunday",0,0,DateTimeRule(1,29,1,false,0,2),2000,2030)
        assertEquals(Grego.fieldsToDay(2023,1,26)*86_400_000,leap.getStartInYear(2023,0,0)!!.getTime())
        assertEquals(Grego.fieldsToDay(2024,1,25)*86_400_000,leap.getStartInYear(2024,0,0)!!.getTime())
    }
    @Test fun ruleIdentityInitialOffsetsAndTransitionAliases() {
        val date = DateTimeRule(0,1,0,2)
        val a = AnnualTimeZoneRule("A",0,0,date,2000,2001)
        val b = AnnualTimeZoneRule("B",0,0,date,2000,2001)
        assertTrue(a.isEquivalentTo(b))
        assertFalse(a.isEquivalentTo(AnnualTimeZoneRule("A",0,0,DateTimeRule(0,1,0,2),2000,2001)))
        val initial = InitialTimeZoneRule(null,0,0)
        assertFalse(initial.isTransitionRule()); assertNull(initial.getFirstStart(0,0)); assertNull(initial.getFinalStart(0,0))
        assertNull(initial.getNextStart(0,0,0,true)); assertNull(initial.getPreviousStart(0,0,0,false))
        assertTrue(initial.isEquivalentTo(InitialTimeZoneRule("other",0,0))); assertFalse(initial.isEquivalentTo(a))
        val transition = TimeZoneTransition(Long.MIN_VALUE,initial,a)
        assertEquals(Long.MIN_VALUE,transition.getTime()); assertSame(initial,transition.getFrom()); assertSame(a,transition.getTo())
        assertEquals("time=0, from={null}, to={null}",TimeZoneTransition(0,null,null).toString())
    }
    @Test fun timeArraysCopiesDuplicatesAndFreshMutableDates() {
        val times = longArrayOf(3000,1000,1000,2000)
        val rule = TimeArrayTimeZoneRule("array",100,50,times,0)
        times[0] = -100
        assertContentEquals(longArrayOf(1000,1000,2000,3000),rule.getStartTimes())
        rule.getStartTimes()[0] = -500
        assertEquals(850,rule.getFirstStart(100,50)!!.getTime())
        assertEquals(2850,rule.getFinalStart(100,50)!!.getTime())
        assertEquals(850,rule.getNextStart(850,100,50,true)!!.getTime())
        assertEquals(1850,rule.getNextStart(850,100,50,false)!!.getTime())
        assertEquals(850,rule.getPreviousStart(1850,100,50,false)!!.getTime())
        assertNull(rule.getNextStart(2850,100,50,false))
        val date = rule.getFirstStart(100,50)!!; date.setTime(99)
        assertEquals(850,rule.getFirstStart(100,50)!!.getTime()); assertNotSame(date,rule.getFirstStart(100,50))
        assertFailsWith<IllegalArgumentException> { TimeArrayTimeZoneRule("",0,0,null,2) }
        assertFailsWith<IllegalArgumentException> { TimeArrayTimeZoneRule("",0,0,longArrayOf(),2) }
    }
    @Test fun mutableDateBoundaryRetainsAllLongBits() {
        for(t in listOf(Long.MIN_VALUE,-9_007_199_254_740_993L,-1L,0L,9_007_199_254_740_993L,Long.MAX_VALUE)) {
            val a=Date(t); val b=Date(t)
            assertEquals(t,a.getTime()); assertEquals(a,b); assertEquals((t xor (t ushr 32)).toInt(),a.hashCode())
            assertEquals(0,a.compareTo(b)); assertFalse(a.before(b)); assertFalse(a.after(b))
            val c=a.clone() as Date
            assertNotSame(a,c); assertEquals(a,c)
            c.setTime(t xor 123L); assertEquals(t,a.getTime()); assertNotEquals(a,c)
        }
        assertTrue(Date(Long.MIN_VALUE).before(Date(Long.MAX_VALUE)))
        assertTrue(Date(Long.MAX_VALUE).after(Date(Long.MIN_VALUE)))
        assertFailsWith<NullPointerException> { Date(0).before(null) }
    }
}
