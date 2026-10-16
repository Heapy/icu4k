package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.Grego
import kotlin.test.*

class ChineseCalendarCoreTest {
    private class Owner: ChineseCalendarCore.Owner {
        val fields = IntArray(24)
        val stamps = IntArray(24)
        val trace = ArrayList<String>()
        var gregorian = IntArray(6)
        var resolution = 2
        var maximum = 30
        var failure: String? = null
        var copy: ChineseCalendarCore.Copy? = null
        var rollAction: ((Int, Int)->Unit)? = null
        var nextStamp = 2
        val core = ChineseCalendarCore(this)
        private fun event(value: String) { trace.add(value); if (value == failure) error(value) }
        override fun set(field: Int,value: Int) { event("set:$field:$value"); fields[field] = value; stamps[field] = nextStamp++ }
        override fun get(field: Int): Int { event("get:$field"); return fields[field] }
        override fun roll(field: Int,amount: Int) { event("roll:$field:$amount"); rollAction?.invoke(field,amount) }
        override fun setLenient(value: Boolean) { event("lenient:$value") }
        override fun prepareGetActual(field: Int,minimum: Boolean) { event("prepare:$field:$minimum") }
        override fun internalGet(field: Int): Int { event("internalGet:$field"); return fields[field] }
        override fun internalGet(field: Int,defaultValue: Int): Int { event("internalGet:$field:$defaultValue"); return if (stamps[field] != 0) fields[field] else defaultValue }
        override fun internalSet(field: Int,value: Int) { event("internalSet:$field:$value"); fields[field] = value }
        override fun newestStamp(first: Int,last: Int,best: Int): Int { event("newest:$first:$last:$best"); return (first..last).fold(best) { value,field -> maxOf(value,stamps[field]) } }
        override fun getStamp(field: Int): Int { event("stamp:$field"); return stamps[field] }
        override fun resolveFields(table: Array<Array<IntArray>>): Int { event("resolve"); return resolution }
        override fun computeGregorianFields(julianDay: Int) { event("gregorian:$julianDay"); gregorian = Grego.dayToFields((julianDay - 2440588).toLong(),null) }
        override fun getGregorianYear(): Int { event("gregorianYear"); return gregorian[0] }
        override fun getGregorianMonth(): Int { event("gregorianMonth"); return gregorian[1] }
        override fun complete() { event("complete") }
        override fun getActualMaximum(field: Int): Int { event("maximum:$field"); return maximum }
        override fun superGetActualMaximum(field: Int): Int { event("baseMaximum:$field"); return maximum }
        override fun superAdd(field: Int,amount: Int) { event("baseAdd:$field:$amount") }
        override fun superRoll(field: Int,amount: Int) { event("baseRoll:$field:$amount") }
        override fun superGetTemporalMonthCode(): String { event("baseCode"); return "M" + (fields[2] + 1).toString().padStart(2,'0') }
        override fun superSetTemporalMonthCode(value: String?) { event("baseSetCode:$value") }
        override fun cloneCalendar(): ChineseCalendarCore.Copy { event("clone"); return copy ?: error("unexpected clone") }
    }
    @Test fun standaloneIcu4jSnapshotsIncludingRareLeapEleventhMonths() {
        // Independently captured from ICU4J 78.3 in Etc/GMT. Field order:
        // EXTENDED_YEAR, ERA, YEAR, MONTH, ORDINAL_MONTH, DATE, IS_LEAP_MONTH, DAY_OF_YEAR.
        val data = arrayOf(
            intArrayOf(2383603,1813,75,10,10,11,3,1,328),
            intArrayOf(2414136,1897,76,34,6,6,1,0,179),
            intArrayOf(2419391,1911,76,48,10,11,1,0,325),
            intArrayOf(2419403,1911,76,48,10,11,13,0,337),
            intArrayOf(2440588,1969,77,46,10,10,24,0,319),
            intArrayOf(2460026,2023,78,40,1,2,1,1,60),
            intArrayOf(2460351,2024,78,41,0,0,1,0,1),
            intArrayOf(2463954,2033,78,50,10,11,1,1,326),
            intArrayOf(2463983,2033,78,50,11,12,1,0,355),
            intArrayOf(2464013,2034,78,51,0,0,1,0,1),
        )
        for (dangi in listOf(false,true)) {
            val o = Owner(); val c = if (dangi) DangiCalendarCore.create(o) else o.core
            for (row in data) {
                o.computeGregorianFields(row[0]); c.handleComputeFields(row[0])
                val expected = row.drop(1).toMutableList()
                if (dangi && row[0] == 2414136) expected[5] = 2
                assertEquals(expected,listOf(19,0,1,2,23,5,22,6).map { o.fields[it] },"$dangi/${row[0]}")
                val code = "M" + (o.fields[2]+1).toString().padStart(2,'0') + if (o.fields[22] == 1) "L" else ""
                assertEquals(code,c.getTemporalMonthCode())
                val year = o.fields[19]; val month = o.fields[2]; val dom = o.fields[5]
                assertEquals(row[0]-dom,c.handleComputeMonthStart(year,month,true),"start/$dangi/${row[0]}")
                assertTrue(c.handleGetMonthLength(year,month) in 29..30)
            }
        }
    }
    @Test fun dangiHistoricalOffsetRulesAndJulianConversion() {
        for ((year,before,after) in listOf(Triple(1897,8,7),Triple(1898,7,8),Triple(1912,8,9))) {
            val transition = (year - 1970) * 365L * 86400000L - before * 3600000L
            assertEquals(before * 3600000,DangiCalendarCore.astronomicalOffset(transition-1))
            assertEquals(after * 3600000,DangiCalendarCore.astronomicalOffset(transition))
            assertEquals(after * 3600000,DangiCalendarCore.astronomicalOffset(transition+1))
        }
        val c = Owner().core
        assertEquals(-28800000L,c.daysToMillis(0)); assertEquals(0,c.millisToDays(-28800000))
        assertEquals(-1,c.millisToDays(-28800001)); assertEquals(0,c.millisToDays(0))
        assertEquals("dangi",DangiCalendarCore.getType()); assertEquals("chinese",c.getType()); assertFalse(c.haveDefaultCentury())
        assertFailsWith<IllegalArgumentException> { c.isLeapMonthBetween(0,1500) }
        assertFalse(c.isLeapMonthBetween(1,0))
    }
    @Test fun extendedYearResolutionLimitsAndFieldPublicationFailure() {
        val o = Owner(); val c = o.core
        assertEquals(1,c.handleGetExtendedYear())
        assertEquals(listOf("newest:0:1:0","stamp:19","internalGet:19:1"),o.trace)
        o.set(0,78); o.set(1,41); assertEquals(2024,c.handleGetExtendedYear())
        o.set(19,2033); assertEquals(2033,c.handleGetExtendedYear())
        assertEquals(12,c.handleGetLimit(23,3)); assertEquals(385,c.handleGetLimit(6,3))
        assertFailsWith<IndexOutOfBoundsException> { c.handleGetLimit(4,0) }
        assertFailsWith<IndexOutOfBoundsException> { c.handleGetLimit(24,0) }
        o.computeGregorianFields(2460351); o.trace.clear(); o.fields.fill(-1)
        o.failure = "internalSet:23:0"
        assertFailsWith<IllegalStateException> { c.handleComputeFields(2460351) }
        assertEquals(listOf("gregorianYear","gregorianMonth","internalSet:19:2024","internalSet:0:78","internalSet:1:41","internalSet:2:0","internalSet:23:0"),o.trace)
        assertEquals(0,o.fields[2]); assertEquals(-1,o.fields[23]); assertEquals(-1,o.fields[5])
    }
    @Test fun monthArithmeticPinningDelegationAndPartialFailures() {
        for (amount in listOf(-1,1)) for (dom in listOf(29,30)) for (maximum in listOf(29,30)) {
            val o = Owner(); val c = o.core
            o.fields[5] = dom; o.fields[20] = 2460351 + dom - 1; o.maximum = maximum
            c.add(2,amount)
            val targetStart = if (amount == 1) 2460380 else 2460321
            val target = targetStart + dom - 1
            val expected = mutableListOf("get:5","get:20")
            if (dom > 29) { expected.add("set:20:${target-1}"); expected.add("complete"); expected.add("maximum:5"); if (maximum >= dom) expected.add("set:20:$target") }
            else expected.add("set:20:$target")
            assertEquals(expected,o.trace,"$amount/$dom/$maximum")
        }
        val o = Owner(); val c = o.core
        c.add(2,0); c.roll(23,0); assertTrue(o.trace.isEmpty())
        c.add(5,0); c.roll(5,0); assertEquals(listOf("baseAdd:5:0","baseRoll:5:0"),o.trace)
        o.trace.clear(); o.fields[5] = 30; o.fields[20] = 2460380; o.failure = "complete"
        assertFailsWith<IllegalStateException> { c.add(23,1) }
        assertEquals(listOf("get:5","get:20","set:20:2460408","complete"),o.trace)
        assertEquals(2460408,o.fields[20])
    }
    @Test fun temporalCodeSideEffectsAndOrdinalMonthCloneOrdering() {
        val o = Owner(); val c = o.core
        c.setTemporalMonthCode("M02L"); assertEquals(listOf("set:2:1","set:22:1"),o.trace)
        o.trace.clear(); assertEquals("M02L",c.getTemporalMonthCode()); assertEquals(listOf("get:22","get:2"),o.trace)
        o.trace.clear(); assertFailsWith<IllegalArgumentException> { c.setTemporalMonthCode("M13L") }; assertTrue(o.trace.isEmpty())
        assertFailsWith<NullPointerException> { c.setTemporalMonthCode(null) }; assertTrue(o.trace.isEmpty())
        c.setTemporalMonthCode("M13"); assertEquals(listOf("set:22:0","baseSetCode:M13"),o.trace)
        o.trace.clear(); o.fields[2] = 12; o.fields[22] = 1
        assertFailsWith<IndexOutOfBoundsException> { c.getTemporalMonthCode() }
        o.trace.clear(); o.fields[2] = 3; o.fields[22] = 0
        assertEquals("M04",c.getTemporalMonthCode()); assertEquals(listOf("get:22","baseCode"),o.trace)
        val copy = Owner(); o.copy = copy; o.resolution = 23; o.fields[23] = 2
        copy.rollAction = { field,amount -> assertEquals(2,field); assertEquals(2,amount); copy.fields[22] = 1; copy.fields[2] = 1 }
        o.trace.clear(); assertEquals(1,c.internalGetMonth(99))
        assertEquals(listOf("resolve","resolve","clone","internalGet:23","internalSet:22:1","internalSet:2:1"),o.trace)
        assertEquals(listOf("set:2:0","set:22:0","set:5:1","roll:2:2","get:22","get:2"),copy.trace)
    }
    @Test fun actualMaximumUsesCloneFieldsAndOriginalCalculationOwner() {
        val o = Owner(); val copy = Owner(); o.copy = copy
        copy.fields[19] = 2024; copy.fields[2] = 0; copy.fields[22] = 0
        assertEquals(29,o.core.getActualMaximum(5))
        assertEquals(listOf("lenient:true","prepare:5:false","get:19","get:2","get:22"),copy.trace)
        assertTrue(o.trace[0] == "clone"); assertTrue(o.trace.any { it.startsWith("gregorian:") })
        o.trace.clear(); o.maximum = 384; assertEquals(384,o.core.getActualMaximum(6)); assertTrue(o.core.inTemporalLeapYear())
        assertEquals(listOf("baseMaximum:6","maximum:6"),o.trace)
    }
    @Test fun clonedCoreSharesWarmCachesAndRebindsFieldCallbacks() {
        val first = Owner(); val second = Owner()
        var calls = 0
        val c = ChineseCalendarCore(first) { calls++; 28800000 }
        val start = c.newYear(2033)
        val clone = c.copyForOwner(second)
        val before = calls
        assertEquals(start,clone.newYear(2033)); assertEquals(before,calls)
        second.computeGregorianFields(2463954); clone.handleComputeFields(2463954)
        assertEquals(1,second.fields[22]); assertTrue(first.fields.all { it == 0 })
        assertSame(c.getFieldResolutionTable(),clone.getFieldResolutionTable())
    }

}
