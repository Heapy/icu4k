@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.util.Calendar
import com.ibm.icu.util.TimeZone
import io.github.heapy.icu4k.impl.calendar.GregorianCalendarCoreOracleTest.Probe
import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

class CalendarTimeCoreOracleTest {
    private class PlainZone(private val delegate: TimeZone) : TimeZone() {
        val calls = ArrayList<String>()
        init { id = delegate.id }
        override fun getRawOffset() = delegate.rawOffset
        override fun setRawOffset(value: Int) { delegate.rawOffset = value }
        override fun useDaylightTime() = delegate.useDaylightTime()
        override fun inDaylightTime(date: java.util.Date?) = delegate.inDaylightTime(date)
        override fun getOffset(era: Int,year: Int,month: Int,day: Int,dow: Int,millis: Int) = delegate.getOffset(era,year,month,day,dow,millis)
        override fun getOffset(date: Long): Int { calls.add("total:$date"); return delegate.getOffset(date) }
        override fun getOffset(date: Long,local: Boolean,offsets: IntArray) { calls.add("offset:$date:$local:${offsets.toList()}"); delegate.getOffset(date,local,offsets) }
    }
    private fun compare(p: Probe, setup: () -> Unit = {}, zone: PlainZone? = null, action: () -> Any?) {
        p.common = false; setup(); p.trace.clear(); zone?.calls?.clear()
        val expected = outcome(action); val time = p.rawTime(); val fields = p.rawFields().toList(); val stamps = p.rawStamps().toList()
        val trace = p.trace.toList(); val zoneTrace = zone?.calls?.toList()
        p.common = true; setup(); p.trace.clear(); zone?.calls?.clear()
        assertEquals(expected,outcome(action))
        assertEquals(time,p.rawTime()); assertEquals(fields,p.rawFields().toList()); assertEquals(stamps,p.rawStamps().toList())
        assertEquals(trace,p.trace); assertEquals(zoneTrace,zone?.calls)
    }

    @Test fun gapsOverlapsStrictnessAndNonBasicFallbacks() {
        val cases = listOf(
            intArrayOf(2024,2,10,1,30),intArrayOf(2024,2,10,2,0),intArrayOf(2024,2,10,2,30),intArrayOf(2024,2,10,3,0),
            intArrayOf(2024,10,3,1,0),intArrayOf(2024,10,3,1,30),intArrayOf(2024,10,3,2,0),
            intArrayOf(2024,3,7,1,45),intArrayOf(2024,9,6,2,15),intArrayOf(2011,11,30,12,0),
            intArrayOf(2024,2,31,1,30),intArrayOf(2024,9,27,1,30),intArrayOf(2024,0,1,0,0),
        )
        for (id in listOf("America/New_York","Europe/London","Australia/Lord_Howe","Pacific/Apia","Europe/Dublin","Africa/Casablanca","Asia/Kathmandu","Etc/GMT")) {
            for (plain in listOf(false,true)) {
                val base = TimeZone.getTimeZone(id)
                val wrapped = if (plain) PlainZone(base) else null
                val p = Probe(id)
                for (date in cases) for (repeat in 0..1) for (skip in 0..2) for (lenient in listOf(false,true)) {
                    compare(p, {
                        p.assignTime(123); p.clear(); p.timeZone = wrapped ?: base; p.isLenient = lenient
                        p.repeatedWallTimeOption = repeat; p.skippedWallTimeOption = skip
                        p.set(Calendar.EXTENDED_YEAR,date[0]); p.set(Calendar.MONTH,date[1]); p.set(Calendar.DAY_OF_MONTH,date[2])
                        p.set(Calendar.HOUR_OF_DAY,date[3]); p.set(Calendar.MINUTE,date[4])
                    },wrapped) { p.computeRawTime() }
                }
            }
        }
    }

    @Test fun fieldPrecedenceWideHoursAndExplicitOffsetOverflow() {
        val p = Probe(); val random = Random(783548)
        repeat(3000) {
            val values = IntArray(24)
            values[19] = random.nextInt(-1000,3000); values[2] = random.nextInt(-20,20); values[5] = random.nextInt(-10,40)
            for (field in 9..16) values[field] = listOf(Int.MIN_VALUE,-549,-548,-1,0,1,548,549,Int.MAX_VALUE)[random.nextInt(9)]
            values[21] = random.nextInt()
            val stamps = ByteArray(24) { random.nextInt(-1,5).toByte() }
            stamps[19] = 5; stamps[2] = 5; stamps[5] = 5
            compare(p, {
                p.assignTime(123); p.clear(); p.isLenient = true; p.skippedWallTimeOption = 0; p.repeatedWallTimeOption = 0
                values.copyInto(p.rawFields()); stamps.copyInto(p.rawStamps())
            }) { p.computeRawTime() }
        }
        for (id in TimeZone.getAvailableIDs()) {
            val zone = TimeZone.getTimeZone(id); p.timeZone = zone
            for (repeat in 0..1) for (skip in 0..2) for (wall in listOf(0L,1710037800000L,1730597400000L)) {
                p.repeatedWallTimeOption = repeat; p.skippedWallTimeOption = skip
                compare(p) { p.zoneOffset32(wall,123) }
                compare(p) { p.zoneOffset64(wall,Long.MAX_VALUE) }
            }
        }
    }

    @Test fun previousTransitionWindowsAndSubsecondSearch() {
        val p = Probe()
        for (id in listOf("America/New_York","Australia/Lord_Howe","Pacific/Apia","Etc/GMT")) {
            val plain = PlainZone(TimeZone.getTimeZone(id)); p.timeZone = plain
            for (base in listOf(1710054000000L,1710054000001L,1710057600000L,1730613600000L,1325239200000L)) {
                compare(p,zone = plain) { p.immediatePrevious(base) }
                for (duration in listOf(1L,999L,1000L,7200000L,108000000L)) compare(p,zone = plain) { p.searchPrevious(plain,base,duration) }
            }
        }
        val transition = 10000123L
        val scripted = object : TimeZone() {
            override fun getRawOffset() = 0
            override fun setRawOffset(value: Int) = Unit
            override fun useDaylightTime() = false
            override fun inDaylightTime(date: java.util.Date?) = false
            override fun getOffset(era: Int,year: Int,month: Int,day: Int,dow: Int,millis: Int) = 0
            override fun getOffset(date: Long) = if (date < transition) 0 else 1800000
        }
        for (base in listOf(transition,transition + 1,transition + 100,transition + 7200000)) {
            compare(p) { p.searchPrevious(scripted,base,7200000) }
        }
    }

    companion object {
        private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
            val cause = if (e is InvocationTargetException) e.targetException else e
            when(cause) {
                is VirtualMachineError -> throw cause
                is IndexOutOfBoundsException -> "bounds"
                is NullPointerException -> "null"
                else -> cause.javaClass.simpleName + ":" + cause.message
            }
        }
    }
}
