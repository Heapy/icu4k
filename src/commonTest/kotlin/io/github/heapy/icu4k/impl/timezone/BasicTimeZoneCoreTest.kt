// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.util.*
import kotlin.test.*

class BasicTimeZoneCoreTest {
    private class Fixture(val rules: Array<TimeZoneRule?>?, val initial: TimeZoneRule, val transitions: List<TimeZoneTransition>) : BasicTimeZoneCore() {
        override fun getID(): String = "fixture"
        override fun getOffset(date: Long, local: Boolean, offsets: IntArray) {
            val rule = transitions.lastOrNull { it.getTime() <= date }?.getTo() ?: initial
            offsets[0] = rule.getRawOffset(); offsets[1] = rule.getDSTSavings()
        }
        override fun getNextTransition(base: Long, inclusive: Boolean): TimeZoneTransition? = transitions.firstOrNull { it.getTime() > base || inclusive && it.getTime() == base }
        override fun getPreviousTransition(base: Long, inclusive: Boolean): TimeZoneTransition? = transitions.lastOrNull { it.getTime() < base || inclusive && it.getTime() == base }
        override fun getTimeZoneRules(): Array<TimeZoneRule?>? = rules
    }
    @Test fun equivalentTransitionsAndIgnoredDstRedistribution() {
        val initial = InitialTimeZoneRule("initial",0,1)
        val changed = InitialTimeZoneRule("changed",-1,2)
        val a = Fixture(arrayOf(initial,changed),initial,listOf(TimeZoneTransition(5,initial,changed)))
        val b = Fixture(arrayOf(initial),initial,emptyList())
        assertFalse(a.hasEquivalentTransitions(b,0,10))
        assertTrue(a.hasEquivalentTransitions(b,0,10,true))
        assertTrue(a.hasEquivalentTransitions(a,Long.MAX_VALUE,Long.MIN_VALUE))
        assertFalse(a.hasEquivalentTransitions(null,0,10))
        val standard = InitialTimeZoneRule("std",1,0)
        val c = Fixture(arrayOf(initial,standard),initial,listOf(TimeZoneTransition(5,initial,standard)))
        assertFalse(c.hasEquivalentTransitions(b,0,10,true))
    }
    @Test fun filteredRulesPreserveOriginalArraysOrTrimTimes() {
        val initial = InitialTimeZoneRule("initial",0,0)
        val array = TimeArrayTimeZoneRule("array",0,1,longArrayOf(100,200,300),2)
        val rules = arrayOf<TimeZoneRule?>(initial,array)
        val zone = Fixture(rules,initial,listOf(TimeZoneTransition(100,initial,array),TimeZoneTransition(200,array,array),TimeZoneTransition(300,array,array)))
        assertSame(rules,zone.getTimeZoneRules(50))
        val filtered = zone.getTimeZoneRules(150)!!
        assertEquals("array",filtered[0]!!.getName()); assertIs<InitialTimeZoneRule>(filtered[0])
        assertContentEquals(longArrayOf(200,300),(filtered[1] as TimeArrayTimeZoneRule).getStartTimes())
        val absent = InitialTimeZoneRule("absent",0,1)
        val malformed = Fixture(rules,initial,listOf(TimeZoneTransition(100,initial,array),TimeZoneTransition(200,array,absent)))
        assertEquals("The rule was not found",assertFailsWith<IllegalStateException> { malformed.getTimeZoneRules(150) }.message)
        val empty = Fixture(null,initial,emptyList())
        assertNull(empty.getTimeZoneRules(0))
    }
    @Test fun wholeRulesAndAnnualDateReferencesRetainSourceIdentity() {
        val initial = InitialTimeZoneRule("initial",0,0)
        val old = TimeArrayTimeZoneRule("old",0,0,longArrayOf(0),2)
        val array = TimeArrayTimeZoneRule("future",0,1,longArrayOf(100,200),2)
        val zone = Fixture(arrayOf(initial,old,array),initial,listOf(TimeZoneTransition(0,initial,old),TimeZoneTransition(100,old,array)))
        assertSame(array,zone.getTimeZoneRules(50)!![1])
        val date = DateTimeRule(0,1,0,2)
        val annual = AnnualTimeZoneRule("annual",0,0,date,1990,AnnualTimeZoneRule.MAX_YEAR)
        val annualZone = Fixture(arrayOf(initial,old,annual),initial,listOf(TimeZoneTransition(0,initial,old),TimeZoneTransition(946684800000L,old,annual)))
        val filtered = annualZone.getTimeZoneRules(500)!![1] as AnnualTimeZoneRule
        assertNotSame(annual,filtered); assertSame(date,filtered.getRule()); assertEquals(2000,filtered.getStartYear())
    }
    @Test fun simplifiedRulesUseWallTimeAndRetainInitialOffsets() {
        val std = InitialTimeZoneRule("STD",-18_000_000,0)
        val dst = InitialTimeZoneRule("DST",-18_000_000,3_600_000)
        val zone = Fixture(arrayOf(std,dst),std,listOf(
            TimeZoneTransition(1699164000000L,dst,std),
            TimeZoneTransition(1710054000000L,std,dst),
            TimeZoneTransition(1730613600000L,dst,std),
            TimeZoneTransition(1741503600000L,std,dst),
        ))
        val simplified = zone.getSimpleTimeZoneRulesNear(1704067200000L)
        assertEquals(3,simplified.size)
        assertEquals("DST",simplified[0]!!.getName())
        for(rule in simplified.drop(1)) {
            val annual = rule as AnnualTimeZoneRule
            assertEquals(DateTimeRule.DOW,annual.getRule()!!.getDateRuleType())
            assertEquals(DateTimeRule.WALL_TIME,annual.getRule()!!.getTimeRuleType())
            assertEquals(AnnualTimeZoneRule.MAX_YEAR,annual.getEndYear())
        }
        val fixed = Fixture(arrayOf(std),std,emptyList()).getSimpleTimeZoneRulesNear(0)
        assertEquals(1,fixed.size); assertEquals("fixture",fixed[0]!!.getName()); assertEquals(-18_000_000,fixed[0]!!.getRawOffset())
    }
}
