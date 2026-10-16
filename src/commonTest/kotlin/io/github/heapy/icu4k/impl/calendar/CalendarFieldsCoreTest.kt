package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.Grego
import kotlin.test.*

class CalendarFieldsCoreTest {
    private class Fixture(count: Int = 24) : CalendarFieldsCore.Owner<Unit> {
        val fields = IntArray(count)
        val stamps = ByteArray(count) { 7 }
        val trace = ArrayList<String>()
        val core = CalendarFieldsCore(fields,stamps,this)
        var epochMillis = 0L
        var raw = 0; var dst = 0
        var first = 2; var minimal = 4
        var mask = listOf(0,1,2,5,6,19,22,23).fold(0) { value,bit -> value or (1 shl bit) }
        var offsetHook: (() -> Unit)? = null
        var nullZone = false
        var observedStamps: List<Byte>? = null
        override fun getTimeZone(): Unit? { trace.add("zone"); return if (nullZone) null else Unit }
        override fun getOffset(zone: Unit?, date: Long, local: Boolean, offsets: IntArray) {
            zone!!; trace.add("offset:$date:$local"); offsets[0] = raw; offsets[1] = dst; offsetHook?.invoke()
        }
        override fun getTime() = epochMillis
        override fun getInternalSetMask() = mask
        override fun handleComputeFields(julianDay: Int) {
            trace.add("fields:$julianDay"); observedStamps = stamps.toList()
            fun put(field: Int, value: Int) { fields[field] = value; stamps[field] = 1 }
            put(2,core.gregorianMonth); put(23,core.gregorianMonth)
            put(5,core.gregorianDayOfMonth); put(6,core.gregorianDayOfYear); put(19,core.gregorianYear)
            put(0,if (core.gregorianYear < 1) 0 else 1)
            put(1,if (core.gregorianYear < 1) 1 - core.gregorianYear else core.gregorianYear)
        }
        override fun getFirstDayOfWeek(): Int { trace.add("first"); return first }
        override fun getMinimalDaysInFirstWeek(): Int { trace.add("minimal"); return minimal }
        override fun handleGetYearLength(year: Int): Int { trace.add("length:$year"); return if (Grego.isLeapYear(year)) 366 else 365 }
        override fun weekNumber(desiredDay: Int, dayOfPeriod: Int, dayOfWeek: Int): Int {
            trace.add("week:$desiredDay:$dayOfPeriod:$dayOfWeek")
            return core.weekNumber(desiredDay,dayOfPeriod,dayOfWeek)
        }
        override fun internalGet(field: Int): Int { trace.add("get:$field"); return fields[field] }
    }

    @Test fun epochFieldsStampTimingAndTimezoneReentry() {
        val f = Fixture()
        f.core.computeFields()
        assertContentEquals(intArrayOf(1,1970,0,1,1,1,1,5,1,0,0,0,0,0,0,0,0,1970,4,1970,2440588,0,0,0),f.fields)
        assertEquals((0..23).map { if (f.mask and (1 shl it) == 0) 1.toByte() else 0.toByte() },f.observedStamps)
        assertEquals(0,f.stamps[22].toInt())
        assertTrue(f.stamps.filterIndexed { index,_ -> index != 22 }.all { it.toInt() == 1 })
        assertEquals(listOf("zone","offset:0:false","first","fields:2440588","first","first","minimal","length:1970","week:1:1:5","first","minimal"),f.trace)
        f.trace.clear(); f.raw = 1000; f.offsetHook = { f.epochMillis = 86400000 }
        f.core.computeFields()
        assertEquals(2,f.fields[5]); assertEquals(1000,f.fields[21]); assertEquals(1000,f.fields[15])
        assertEquals("offset:0:false",f.trace[1])
        val missing = Fixture(); missing.nullZone = true
        assertFailsWith<NullPointerException> { missing.core.computeFields() }
        assertEquals(listOf("zone"),missing.trace)
        assertTrue(missing.stamps.all { it.toInt() == 7 })
    }

    @Test fun isoWeekBoundariesNegativeTimeAndCopyIndependence() {
        val f = Fixture(); f.epochMillis = 1609459200000L // Friday January 1, 2021.
        f.core.computeFields()
        assertEquals(53,f.fields[3]); assertEquals(2020,f.fields[17]); assertEquals(0,f.fields[4])
        val copy = f.core.copyFor(f.fields.copyOf(),f.stamps.copyOf(),f)
        f.epochMillis = -1; f.core.computeFields()
        assertEquals(1969,f.fields[19]); assertEquals(86399999,f.fields[21])
        assertEquals(23,f.fields[11]); assertEquals(11,f.fields[10]); assertEquals(1,f.fields[9])
        assertEquals(999,f.fields[14])
        assertEquals(2021,copy.gregorianYear)
        val extended = Fixture(32); extended.mask = -1
        extended.core.computeFields()
        assertTrue(extended.stamps.slice(24..31).all { it.toInt() == 0 })
    }

    @Test fun precedenceRemappingTiesAndByteStampBoundaries() {
        val f = Fixture(); f.stamps.fill(0)
        assertEquals(-1,f.core.resolveFields(CalendarFieldsCore.DATE_PRECEDENCE))
        f.stamps[3] = 4; f.stamps[7] = 3; f.stamps[5] = 4
        assertEquals(5,f.core.resolveFields(CalendarFieldsCore.DATE_PRECEDENCE)) // First matching line wins a tie.
        f.stamps[3] = 5
        assertEquals(3,f.core.resolveFields(CalendarFieldsCore.DATE_PRECEDENCE))
        f.stamps.fill(0); f.stamps[1] = 7; f.stamps[5] = 1
        assertEquals(5,f.core.resolveFields(CalendarFieldsCore.DATE_PRECEDENCE))
        f.stamps[4] = 9; f.stamps[5] = 0
        assertEquals(4,f.core.resolveFields(CalendarFieldsCore.DATE_PRECEDENCE)) // DATE remap rejected by newer WOM.
        f.stamps[2] = -128
        assertEquals(-128,f.core.getStamp(2)); assertEquals(9,f.core.newestStamp(0,23,0))
        assertEquals(2,f.core.newerField(2,2))
        assertEquals(7,f.core.newestStamp(4,3,7))
        assertFailsWith<NullPointerException> { f.core.resolveFields(null) }
        assertFailsWith<NullPointerException> { f.core.resolveFields(arrayOf(null)) }
        assertFailsWith<IndexOutOfBoundsException> { f.core.resolveFields(arrayOf(arrayOf(intArrayOf()))) }
        assertFailsWith<IndexOutOfBoundsException> { f.core.getStamp(24) }
    }

    @Test fun hourSelectionAndWideMillisPreserveIntAmPmMultiplication() {
        val f = Fixture(); f.stamps.fill(0)
        f.fields[11] = 7; f.fields[10] = 4; f.fields[9] = 1
        f.stamps[11] = 3; f.stamps[10] = 3
        assertEquals(25200000L,f.core.computeMillisInDayLong())
        f.stamps[9] = 4
        assertEquals(57600000L,f.core.computeMillisInDayLong())
        f.fields[10] = Int.MAX_VALUE; f.fields[9] = Int.MAX_VALUE
        f.trace.clear()
        val wide = (Int.MAX_VALUE.toLong() + (12 * Int.MAX_VALUE).toLong()) * 3600000L
        assertEquals(wide,f.core.computeMillisInDayLong())
        assertEquals(wide.toInt(),f.core.computeMillisInDay())
        assertEquals(listOf("get:10","get:9","get:12","get:13","get:14","get:10","get:9","get:12","get:13","get:14"),f.trace)
    }
}
