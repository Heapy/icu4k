package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.Grego
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class EraRulesCoreTest {
    private class Node(private val key: String?,private val children: List<Node> = emptyList(),private val vector: IntArray?=null,private val string: String?=null) : UResourceBundle() {
        override fun getULocale()=ULocale.ROOT
        override fun getLocaleID()=""
        override fun getBaseName()="test"
        override fun getParent(): UResourceBundle?=null
        override fun getKey()=key
        override fun getSize()=children.size
        override fun get(index: Int): UResourceBundle=children[index]
        override fun getIntVector()=vector!!
        override fun getString()=string!!
    }
    private class Clock(var time: Long=0,var offset: Int=0) : EraRulesCore.Owner {
        val trace=ArrayList<String>()
        override fun currentTimeMillis(): Long { trace.add("clock"); return time }
        override fun defaultZoneOffset(time: Long): Int { trace.add("offset:$time"); return offset }
    }
    private fun rule(key: String?,date: IntArray?=null,tentative: Boolean=false,end: Boolean=false): Node {
        val children=ArrayList<Node>()
        if(date!=null) children.add(Node("start",vector=date))
        if(tentative) children.add(Node("named",string="false"))
        if(end) children.add(Node("end"))
        return Node(key,children)
    }
    private fun load(vararg rules: Node,tentative: Boolean=true,clock: Clock=Clock())=EraRulesCore.fromResource("test",Node("eras",rules.toList()),tentative,clock)

    @Test fun sparseCodesTentativeTrimmingAndFillAliases() {
        val a=rule("7",intArrayOf(2000,1,1)); val b=rule("9",intArrayOf(2010,2,3)); val c=rule("12",intArrayOf(2020,4,5),tentative=true)
        val full=load(a,b,c); val trimmed=load(a,b,c,tentative=false)
        assertEquals(3,full.getNumberOfEras()); assertEquals(12,full.getMaxEraCode())
        assertEquals(2,trimmed.getNumberOfEras()); assertEquals(9,trimmed.getMaxEraCode())
        assertEquals(7,full.getEraCode(1990,1,1)); assertEquals(9,full.getEraCode(2010,2,3)); assertEquals(12,full.getEraCode(2020,4,5))
        assertFailsWith<IllegalArgumentException> { full.getStartYear(8) }
        val fill=IntArray(4) { 123 }; assertSame(fill,full.getStartDate(9,fill)); assertContentEquals(intArrayOf(2010,2,3,123),fill)
        val small=intArrayOf(123); assertNotSame(small,full.getStartDate(9,small)); assertContentEquals(intArrayOf(123),small)
        assertFailsWith<IllegalArgumentException> { trimmed.getStartYear(12) }
    }

    @Test fun unicodeDecimalEraKeysMatchJavaParsing() {
        assertEquals(1,load(rule("\u0661",intArrayOf(2000,1,1))).getMaxEraCode())
        assertEquals(1,load(rule("+\uff11",intArrayOf(2000,1,1))).getMaxEraCode())
        assertFailsWith<ICUException> { load(rule("2147483648",intArrayOf(2000,1,1))) }
    }

    @Test fun minimumSentinelAndReverseEraSearch() {
        val rules=load(rule("0",intArrayOf(1,1,1)),rule("1",end=true))
        assertContentEquals(intArrayOf(Int.MIN_VALUE,1,1),rules.getStartDate(1,null))
        assertEquals(1,rules.getEraCode(Int.MIN_VALUE,1,1)); assertEquals(0,rules.getEraCode(1,1,1))
        assertEquals(1,rules.getEraCode(-32769,12,31)); assertEquals(0,rules.getEraCode(32768,1,1))
        val encoded=load(rule("0",intArrayOf(-32768,1,1)))
        assertEquals(Int.MIN_VALUE,encoded.getStartYear(0))
        // Validation intentionally accepts days up to 31 independently of month length.
        assertEquals(2024,load(rule("0",intArrayOf(2024,2,31))).getStartYear(0))
        assertEquals("Illegal date - year:0month:13day:1",assertFailsWith<IllegalArgumentException> { encoded.getEraCode(0,13,1) }.message)
    }

    @Test fun validationErrorsPrecedeClockAndPreserveSourceMessages() {
        val cases=listOf(
            arrayOf(rule("bad",intArrayOf(1,1,1))) to "Invalid era rule key:bad in era rule data for test",
            arrayOf(rule(null,intArrayOf(1,1,1))) to "Invalid era rule key:null in era rule data for test",
            arrayOf(rule("-1",intArrayOf(1,1,1))) to "Era rule key:-1 in era rule data for test must be >= 0",
            arrayOf(rule("0",intArrayOf(1,1))) to "Invalid era rule date data:[1, 1] in era rule data for test",
            arrayOf(rule("0",intArrayOf(32768,1,1))) to "Invalid era rule date data:[32768, 1, 1] in era rule data for test",
            arrayOf(rule("0")) to "Missing era start/end rule date for key:0 in era rule data for test",
            arrayOf(rule("0",intArrayOf(1,1,1)),rule("0",intArrayOf(2,1,1))) to "Duplicated era rule for rule key:0 in era rule data for test",
            arrayOf(rule("0",intArrayOf(1,1,1),tentative=true),rule("1",intArrayOf(2,1,1))) to "Non-tentative era(1) must be placed before the first tentative era",
        )
        for((rules,message) in cases) {
            val clock=Clock()
            assertEquals(message,assertFailsWith<ICUException> { load(*rules,clock=clock) }.message)
            assertTrue(clock.trace.isEmpty())
        }
    }

    @Test fun currentEraUsesLocalTimeOnceAndRealJapaneseResources() {
        val midnight=Grego.fieldsToDay(2019,4,1)*86400000L
        val clock=Clock(midnight-1,1)
        val rules=load(rule("0",intArrayOf(1989,1,8)),rule("1",intArrayOf(2019,5,1)),clock=clock)
        assertEquals(listOf("clock","offset:${midnight-1}"),clock.trace)
        assertEquals(1,rules.getCurrentEraCode()); clock.time=0; clock.offset=0
        assertEquals(1,rules.getCurrentEraCode()); assertEquals(2,clock.trace.size)
        val japanese=EraRulesCore.getInstance("japanese",false,clock)
        val era=japanese.getEraCode(2019,5,1)
        assertContentEquals(intArrayOf(2019,5,1),japanese.getStartDate(era,null))
        assertTrue(japanese.getEraCode(2019,4,30)<era)
    }
}
