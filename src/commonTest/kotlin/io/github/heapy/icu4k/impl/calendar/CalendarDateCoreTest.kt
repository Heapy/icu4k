package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.Grego
import kotlin.test.*

class CalendarDateCoreTest {
    private class Fixture : CalendarDateCore.Owner, CalendarFieldsCore.Owner<Unit> {
        val fields = IntArray(24); val stamps = ByteArray(24)
        val trace = ArrayList<String>()
        val date = CalendarDateCore(fields,stamps,this)
        val fieldCore = CalendarFieldsCore(fields,stamps,this)
        var year = 1970
        var defaultDayHook: (() -> Unit)? = null
        fun set(field: Int,value: Int,stamp: Int = 2) { fields[field] = value; stamps[field] = stamp.toByte() }
        override fun newestStamp(first: Int,last: Int,best: Int) = fieldCore.newestStamp(first,last,best)
        override fun resolveFields(table: Array<Array<IntArray>>?) = fieldCore.resolveFields(table)
        override fun getFieldResolutionTable() = CalendarFieldsCore.DATE_PRECEDENCE
        override fun newerField(defaultField: Int,alternateField: Int) = fieldCore.newerField(defaultField,alternateField)
        override fun internalGet(field: Int) = date.internalGet(field)
        override fun internalGet(field: Int,defaultValue: Int) = date.internalGet(field,defaultValue)
        override fun internalGetMonth() = date.internalGetMonth()
        override fun internalGetMonth(defaultValue: Int) = date.internalGetMonth(defaultValue)
        override fun internalSet(field: Int,value: Int) { trace.add("set:$field:$value"); date.internalSet(field,value) }
        override fun handleGetExtendedYear(): Int { trace.add("extended"); return year }
        override fun handleComputeJulianDay(bestField: Int): Int { trace.add("julian:$bestField"); return date.handleComputeJulianDay(bestField) }
        override fun handleComputeMonthStart(year: Int,month: Int,useMonth: Boolean): Int {
            trace.add("start:$year:$month:$useMonth"); return CalendarDateCore.computeGregorianMonthStart(year,month)
        }
        override fun getDefaultMonthInYear(year: Int): Int { trace.add("defaultMonth"); return 0 }
        override fun getDefaultDayInMonth(year: Int,month: Int): Int { trace.add("defaultDay"); defaultDayHook?.invoke(); return 1 }
        override fun isSet(field: Int) = stamps[field].toInt() != 0
        override fun getFirstDayOfWeek() = 2
        override fun getMinimalDaysInFirstWeek() = 4
        override fun handleGetMonthLength(year: Int,month: Int) = CalendarDateCore.gregorianMonthLength(year,month)
        override fun handleGetYearLength(year: Int) = if (Grego.isLeapYear(year)) 366 else 365
        override fun validateField(field: Int) { trace.add("validate:$field"); date.validateField(field) }
        override fun getMinimum(field: Int) = 0
        override fun getMaximum(field: Int) = 100
        override fun fieldName(field: Int) = date.fieldName(field)
        override fun getInternalSetMask() = listOf(0,1,2,5,6,19,22,23).fold(0) { value,bit -> value or (1 shl bit) }
        override fun reportDefaultMonthLength(year: Int,month: Int) { trace.add("diagnostic:$year:$month") }
        override fun getTimeZone(): Unit = error("unused")
        override fun getOffset(zone: Unit?,date: Long,local: Boolean,offsets: IntArray): Unit = error("unused")
        override fun getTime(): Long = error("unused")
        override fun handleComputeFields(julianDay: Int): Unit = error("unused")
        override fun weekNumber(desiredDay: Int,dayOfPeriod: Int,dayOfWeek: Int): Int = error("unused")
    }

    @Test fun defaultDateOrdinalMonthAndVirtualDefaultDaySideEffects() {
        val f = Fixture()
        assertEquals(2440588,f.date.computeJulianDay())
        assertEquals(listOf("julian:5","extended","set:19:1970","defaultMonth","start:1970:0:true","defaultDay"),f.trace)
        f.set(2,0,2); f.set(23,1,3); f.set(5,1,4)
        assertEquals(2440619,f.date.computeJulianDay())
        f.stamps[23] = 0
        assertEquals(2440588,f.date.computeJulianDay())
        f.defaultDayHook = { f.set(5,7) }
        assertEquals(2440594,f.date.handleComputeJulianDay(5)) // Default evaluated before the defaulted field read.
        f.set(5,9,-1)
        assertEquals(9,f.date.internalGet(5))
        assertEquals(123,f.date.internalGet(5,123))
    }

    @Test fun explicitJulianDayPriorityAndWeekOrdinalDates() {
        val f = Fixture()
        f.set(20,1234567,3); f.set(11,23,127)
        assertEquals(1234567,f.date.computeJulianDay()); assertTrue(f.trace.isEmpty())
        f.set(23,1,3)
        assertEquals(1234567,f.date.computeJulianDay())
        f.stamps[23] = 4
        assertEquals(2440619,f.date.computeJulianDay())
        val week = Fixture()
        week.set(17,2020,3); week.set(3,53,4); week.set(7,2,5)
        assertEquals(2459212,week.date.handleComputeJulianDay(3))
        assertFalse("extended" in week.trace)
        val lastSunday = Fixture(); lastSunday.year = 2024
        lastSunday.set(2,1,3); lastSunday.set(8,-1,3); lastSunday.set(7,1,4)
        assertEquals(2460366,lastSunday.date.handleComputeJulianDay(8))
    }

    @Test fun validationSetterMasksAndDefaultLengthCallbacks() {
        val f = Fixture(); f.year = 2024; f.set(2,1); f.set(5,30)
        assertEquals("DAY_OF_MONTH=30, valid range=1..29",assertFailsWith<IllegalArgumentException> { f.date.validateField(5) }.message)
        f.set(8,0)
        assertEquals("DAY_OF_WEEK_IN_MONTH cannot be zero",assertFailsWith<IllegalArgumentException> { f.date.validateField(8) }.message)
        f.set(5,29); f.stamps[8] = 1; f.stamps[2] = -1; f.trace.clear()
        f.date.validateFields()
        assertEquals(listOf("validate:5","extended"),f.trace)
        assertEquals("Subclass cannot set DAY_OF_WEEK",assertFailsWith<IllegalStateException> { f.date.internalSet(7,1) }.message)
        assertFailsWith<IndexOutOfBoundsException> { f.date.internalSet(32,1) }
        f.trace.clear()
        assertEquals(29,f.date.handleGetMonthLength(2024,1))
        assertEquals(listOf("diagnostic:2024:1","start:2024:2:true","start:2024:1:true"),f.trace)
        assertEquals(366,f.date.handleGetYearLength(2024))
        assertEquals("Field 24",f.date.fieldName(24))
    }

    @Test fun floorRemainderBoundsAndGregorianEpochArithmetic() {
        assertEquals(2440588,CalendarDateCore.millisToJulianDay(0))
        assertEquals(2440587,CalendarDateCore.millisToJulianDay(-1))
        assertEquals(0L,CalendarDateCore.julianDayToMillis(2440588))
        assertEquals(2440556,CalendarDateCore.computeGregorianMonthStart(1970,-1))
        assertEquals(31,CalendarDateCore.gregorianPreviousMonthLength(2000,0))
        val remainder = intArrayOf(777,888)
        assertEquals(-178956971,CalendarDateCore.floorDivide(Int.MIN_VALUE,12,remainder))
        assertContentEquals(intArrayOf(4,888),remainder)
        assertEquals(-1,CalendarDateCore.floorDivide(-1L,12,remainder)); assertEquals(11,remainder[0])
        assertEquals(0,CalendarDateCore.floorDivide(Long.MIN_VALUE,1,remainder)); assertEquals(0,remainder[0])
        assertFailsWith<ArithmeticException> { CalendarDateCore.floorDivide(1,0,null) }
        assertFailsWith<NullPointerException> { CalendarDateCore.floorDivide(1,1,null) }
        assertFailsWith<IndexOutOfBoundsException> { CalendarDateCore.floorDivide(1,1,IntArray(0)) }
    }
}
