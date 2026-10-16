package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.timezone.ZoneMetaCore
import kotlin.test.*

class CalendarTimeCoreTest {
    private interface Zone {
        val basic: Boolean
        fun offset(date: Long,local: Boolean,values: IntArray)
        fun local(date: Long,nonExisting: Int,duplicated: Int,values: IntArray)
        fun total(date: Long): Int
        fun previous(base: Long,inclusive: Boolean): CalendarTimeCore.Transition?
    }
    private class OlsonZone(id: String, override val basic: Boolean) : Zone {
        private val zone = ZoneMetaCore.getSystemTimeZoneCore(id)!!
        override fun offset(date: Long,local: Boolean,values: IntArray) = zone.getOffset(date,local,values)
        override fun local(date: Long,nonExisting: Int,duplicated: Int,values: IntArray) = zone.getOffsetFromLocal(date,nonExisting,duplicated,values)
        override fun total(date: Long): Int { val values = IntArray(2); zone.getOffset(date,false,values); return values[0] + values[1] }
        override fun previous(base: Long,inclusive: Boolean): CalendarTimeCore.Transition? {
            val transition = zone.getPreviousTransition(base,inclusive) ?: return null
            return CalendarTimeCore.Transition { transition.getTime() }
        }
    }
    private class ScriptZone(override val basic: Boolean = false) : Zone {
        var offsetHook: (Long,Boolean,IntArray) -> Unit = { _,_,values -> values[0] = 0; values[1] = 0 }
        var totalHook: (Long) -> Int = { 0 }
        var previousHook: (Long,Boolean) -> CalendarTimeCore.Transition? = { _,_ -> null }
        override fun offset(date: Long,local: Boolean,values: IntArray) = offsetHook(date,local,values)
        override fun local(date: Long,nonExisting: Int,duplicated: Int,values: IntArray) = offsetHook(date,true,values)
        override fun total(date: Long) = totalHook(date)
        override fun previous(base: Long,inclusive: Boolean) = previousHook(base,inclusive)
    }
    private class Fixture(assertions: Boolean = false) : CalendarTimeCore.Owner<Zone>, CalendarFieldsCore.Owner<Zone> {
        val fields = IntArray(24); val stamps = ByteArray(24)
        val trace = ArrayList<String>()
        val core = CalendarTimeCore(stamps,this,assertions)
        private val fieldCore = CalendarFieldsCore(fields,stamps,this)
        var storedZone: Zone? = ScriptZone()
        var lenientValue = true; var virtualLenient: Boolean? = null
        var skipped = 0; var repeated = 0
        var resultTime = 123L; var julian = 2440588
        fun localDate(year: Int,month: Int,day: Int,hour: Int,minute: Int) {
            julian = CalendarDateCore.computeGregorianMonthStart(year,month) + day
            fields[11] = hour; fields[12] = minute; stamps[11] = 2; stamps[12] = 3
        }
        override fun isLenient(): Boolean { trace.add("lenient"); return virtualLenient ?: lenientValue }
        override fun getLenientFlag() = lenientValue
        override fun validateFields() { trace.add("validate") }
        override fun computeJulianDay(): Int { trace.add("julian"); return julian }
        override fun newestStamp(first: Int,last: Int,best: Int) = fieldCore.newestStamp(first,last,best)
        override fun internalGet(field: Int): Int { trace.add("get:$field"); return fields[field] }
        override fun computeMillisInDay(): Int { trace.add("millis32"); return fieldCore.computeMillisInDay() }
        override fun computeMillisInDayLong(): Long { trace.add("millis64"); return fieldCore.computeMillisInDayLong() }
        override fun computeZoneOffset(millis: Long,millisInDay: Long): Int { trace.add("zone64"); return core.computeZoneOffset(millis,millisInDay) }
        override fun setTime(time: Long) { trace.add("set:$time"); resultTime = time }
        override fun getZone() = storedZone
        override fun getSkippedWallTime() = skipped
        override fun getRepeatedWallTime() = repeated
        override fun isBasicTimeZone(zone: Zone?) = zone?.basic == true
        override fun getOffsetFromLocal(zone: Zone?,wall: Long,nonExisting: Int,duplicated: Int,offsets: IntArray) {
            zone!!; trace.add("local:$wall:$nonExisting:$duplicated"); zone.local(wall,nonExisting,duplicated,offsets)
        }
        override fun getOffset(zone: Zone?,date: Long,local: Boolean,offsets: IntArray) {
            zone!!; trace.add("offset:$date:$local"); zone.offset(date,local,offsets)
        }
        override fun getTotalOffset(zone: Zone?,date: Long): Int { zone!!; trace.add("total:$date"); return zone.total(date) }
        override fun getPreviousTransition(zone: Zone?,base: Long,inclusive: Boolean): CalendarTimeCore.Transition? { zone!!; trace.add("previous:$base:$inclusive"); return zone.previous(base,inclusive) }
        override fun getTimeZone(): Zone? = error("unused virtual timezone getter")
        override fun getTime() = resultTime
        override fun getInternalSetMask(): Int = error("unused")
        override fun handleComputeFields(julianDay: Int): Unit = error("unused")
        override fun getFirstDayOfWeek(): Int = error("unused")
        override fun getMinimalDaysInFirstWeek(): Int = error("unused")
        override fun handleGetYearLength(year: Int): Int = error("unused")
        override fun weekNumber(desiredDay: Int,dayOfPeriod: Int,dayOfWeek: Int): Int = error("unused")
    }

    @Test fun realOlsonGapsOverlapsAndNonBasicFallbacks() {
        for (basic in listOf(false,true)) {
            val f = Fixture(); f.storedZone = OlsonZone("America/New_York",basic)
            f.localDate(2024,2,10,2,30)
            for ((option,expected) in listOf(0 to 1710055800000L,1 to 1710052200000L,2 to 1710054000000L)) {
                f.skipped = option; f.core.computeTime(); assertEquals(expected,f.resultTime)
            }
            f.lenientValue = false; f.resultTime = 123
            assertFailsWith<IllegalArgumentException> { f.core.computeTime() }; assertEquals(123L,f.resultTime)
            f.lenientValue = true; f.localDate(2024,10,3,1,30)
            for ((option,expected) in listOf(0 to 1730615400000L,1 to 1730611800000L)) {
                f.repeated = option; f.core.computeTime(); assertEquals(expected,f.resultTime)
            }
        }
        val apia = Fixture(); apia.storedZone = OlsonZone("Pacific/Apia",false); apia.skipped = 2
        apia.localDate(2011,11,30,12,0); apia.core.computeTime()
        assertEquals(1325239200000L,apia.resultTime)
    }

    @Test fun wideHourSelectionExplicitOffsetsAndMillisecondsPrecedence() {
        for ((hour,wide) in listOf(548 to false,549 to true,-549 to true,Int.MIN_VALUE to false)) {
            val f = Fixture(); f.fields[11] = hour; f.stamps[11] = 2
            f.core.computeTime(); assertEquals(wide,"millis64" in f.trace); assertEquals(!wide,"millis32" in f.trace)
        }
        val f = Fixture(); f.storedZone = null
        f.fields[21] = 999; f.stamps[21] = 5; f.fields[15] = Int.MAX_VALUE; f.stamps[15] = 2; f.fields[16] = 1
        f.core.computeTime()
        assertEquals(2147484647L,f.resultTime) // raw + dst overflows as Int before subtraction.
        assertFalse("millis32" in f.trace); assertFalse("zone64" in f.trace)
        f.fields[11] = 1; f.stamps[11] = 6; f.trace.clear(); f.core.computeTime()
        assertTrue("millis32" in f.trace)
    }

    @Test fun strictVirtualGetterAndAssertionFailuresPreserveOldTime() {
        val f = Fixture(true); val zone = ScriptZone(true); f.storedZone = zone
        f.skipped = 2; f.lenientValue = false; f.virtualLenient = true; zone.totalHook = { 1 }
        assertFailsWith<IllegalArgumentException> { f.core.computeTime() }
        assertFalse("validate" in f.trace); assertEquals(123L,f.resultTime)
        f.lenientValue = true; f.trace.clear()
        assertEquals("Could not locate a time zone transition before 0",assertFailsWith<RuntimeException> { f.core.computeTime() }.message)
        zone.totalHook = { f.skipped = 0; 1 }; f.skipped = 2
        assertEquals("0",assertFailsWith<AssertionError> { f.core.computeTime() }.message)
        assertEquals(123L,f.resultTime)
        val delta = Fixture(true); delta.repeated = 1
        (delta.storedZone as ScriptZone).totalHook = { 21600000 }
        assertEquals("-21600000",assertFailsWith<AssertionError> { delta.core.computeZoneOffset(0,0) }.message)
        assertFailsWith<AssertionError> { delta.core.getPreviousZoneTransitionTime(delta.storedZone,0,0) }
    }

    @Test fun subsecondSearchAndBasicLocalOptions() {
        val f = Fixture(); val zone = ScriptZone(); f.storedZone = zone
        zone.totalHook = { if (it < 10000123L) 0 else 1800000 }
        assertEquals(10000123L,f.core.getPreviousZoneTransitionTime(zone,11000000,7200000))
        val basic = ScriptZone(true); f.storedZone = basic; f.skipped = 1; f.repeated = 1
        f.trace.clear(); assertEquals(0,f.core.computeZoneOffset(1000,100L))
        assertEquals(listOf("local:1100:12:4"),f.trace)
        basic.previousHook = { _,_ -> CalendarTimeCore.Transition { 777 } }
        assertEquals(777L,f.core.getImmediatePreviousZoneTransition(1000))
    }
}
