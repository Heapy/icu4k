// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import kotlin.test.*

class DateTimeRuleTest {
    @Test fun allFourRuleFormsAndUnvalidatedValues() {
        val dom = DateTimeRule(2,14,7_323_004,DateTimeRule.WALL_TIME)
        assertEquals(DateTimeRule.DOM,dom.getDateRuleType()); assertEquals(14,dom.getRuleDayOfMonth())
        assertEquals(0,dom.getRuleDayOfWeek()); assertEquals(0,dom.getRuleWeekInMonth())
        assertEquals("month=Mar, date=14, time=2:02:03.004(WALL)",dom.toString())
        assertEquals("month=Oct, date=-1Sun, time=0:00:00.000(STD)",DateTimeRule(9,-1,1,0,1).toString())
        assertEquals("month=Feb, date=Mon>=15, time=0:00:00.000(UTC)",DateTimeRule(1,15,2,true,0,2).toString())
        assertEquals("month=Feb, date=Sat<=29, time=0:00:00.000(null)",DateTimeRule(1,29,7,false,0,99).toString())
        val invalid = DateTimeRule(100,-5,-1,-7)
        assertEquals(100,invalid.getRuleMonth()); assertEquals(-5,invalid.getRuleDayOfMonth())
        assertFailsWith<IndexOutOfBoundsException> { invalid.toString() }
        assertNotEquals(dom,DateTimeRule(2,14,7_323_004,DateTimeRule.WALL_TIME))
    }
    @Test fun gettersAreVirtualWhileDebugStringUsesStoredFields() {
        val rule = object : DateTimeRule(0,1,0,0) { override fun getRuleMonth(): Int = 10 }
        assertEquals(10,rule.getRuleMonth()); assertTrue(rule.toString().startsWith("month=Jan"))
    }
}
