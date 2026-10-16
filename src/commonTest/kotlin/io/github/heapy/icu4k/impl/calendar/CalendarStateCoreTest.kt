package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.Grego
import io.github.heapy.icu4k.util.Date
import kotlin.test.*

class CalendarStateCoreTest {
    private class Fixture(assertions: Boolean = true) : CalendarStateCore.Owner {
        val storage=CalendarStateCore.createStorage(IntArray(24))
        val fields=storage.fields; val stamps=storage.stamps
        val values=CalendarStateCore.Values<Any>()
        val trace=ArrayList<String>()
        val core=CalendarStateCore(fields,stamps,values,this,assertions)
        var fieldsHook: () -> Unit = { fields[2]=1; stamps[2]=1 }
        var timeHook: () -> Unit = { values.time=1000 }
        var lenientHook: (() -> Boolean)? = null
        var relatedDifference=0
        var maximum=366
        override fun computeFields() { trace.add("fields"); fieldsHook() }
        override fun computeTime() { trace.add("time"); timeHook() }
        override fun complete() { trace.add("complete"); core.complete() }
        override fun isLenient(): Boolean { trace.add("lenient"); return lenientHook?.invoke() ?: values.lenient }
        override fun getTimeInMillis(): Long { trace.add("getMillis"); return core.getTimeInMillis() }
        override fun setTimeInMillis(millis: Long) { trace.add("setMillis:$millis"); core.setTimeInMillis(millis) }
        override fun getRelatedYearDifference(): Int { trace.add("related"); return relatedDifference }
        override fun getActualMaximum(field: Int): Int { trace.add("maximum:$field"); return maximum }
    }

    @Test fun virtualFieldsInvalidIndicesAndMonthAliasClearing() {
        val f=Fixture(); f.core.setTimeInMillis(77)
        assertTrue(f.trace.isEmpty()); assertTrue(f.core.isSet(-1)); assertTrue(f.core.isSet(24))
        assertFailsWith<IndexOutOfBoundsException> { f.core.get(-1) }
        assertEquals(listOf("complete","fields"),f.trace)
        assertTrue(f.values.areFieldsSet); assertTrue(f.values.areAllFieldsSet); assertTrue(f.values.areFieldsVirtuallySet)
        f.trace.clear()
        assertFailsWith<IndexOutOfBoundsException> { f.core.set(24,9) }
        assertEquals(listOf("fields"),f.trace); assertEquals(2,f.values.nextStamp.toInt())
        f.core.set(2,7)
        assertFalse(f.values.isTimeSet); assertFalse(f.values.areFieldsSet); assertFalse(f.values.areFieldsVirtuallySet)
        assertTrue(f.values.areAllFieldsSet)
        f.core.set(23,8); val next=f.values.nextStamp
        f.core.clear(2)
        assertEquals(0,f.fields[2]); assertEquals(0,f.fields[23]); assertEquals(0,f.stamps[2].toInt()); assertEquals(0,f.stamps[23].toInt())
        assertEquals(next,f.values.nextStamp); assertFalse(f.values.areAllFieldsSet); assertEquals(77L,f.values.time)
        f.core.clear(); assertEquals(2,f.values.nextStamp.toInt()); assertEquals(77L,f.values.time)
        assertFailsWith<IndexOutOfBoundsException> { f.core.isSet(24) }
    }

    @Test fun callbackFailuresAndFlagsFollowSourceOrder() {
        val f=Fixture(); f.values.time=77
        f.timeHook={ error("time failed") }
        assertFailsWith<IllegalStateException> { f.core.getTimeInMillis() }
        assertEquals(listOf("time"),f.trace); assertEquals(77L,f.values.time); assertFalse(f.values.isTimeSet)
        f.trace.clear(); f.timeHook={ f.values.time=88 }; f.lenientHook={ error("lenient failed") }
        assertFailsWith<IllegalStateException> { f.core.getTimeInMillis() }
        assertEquals(listOf("time","lenient"),f.trace); assertEquals(88L,f.values.time); assertFalse(f.values.isTimeSet)
        f.lenientHook=null; f.values.lenient=false
        f.timeHook={ f.values.areAllFieldsSet=true; f.values.areFieldsSet=true; f.values.time=99 }
        assertEquals(99L,f.core.getTimeInMillis()); assertTrue(f.values.areFieldsSet)
        f.values.areFieldsSet=false; f.values.areAllFieldsSet=false
        f.fieldsHook={ f.fields[1]=123; error("fields failed") }
        assertFailsWith<IllegalStateException> { f.core.complete() }
        assertEquals(123,f.fields[1]); assertFalse(f.values.areFieldsSet); assertFalse(f.values.areAllFieldsSet)
        f.fieldsHook={ f.values.isTimeSet=false }; f.core.complete()
        assertFalse(f.values.isTimeSet); assertTrue(f.values.areFieldsSet); assertTrue(f.values.areAllFieldsSet)
    }

    @Test fun stampCompressionAndFailedStoreCounterSideEffects() {
        val f=Fixture()
        f.values.nextStamp=127; f.stamps[1]=100; f.stamps[2]=110; f.stamps[5]=126
        f.core.set(12,59)
        assertEquals(listOf(2,3,4,5),listOf(1,2,5,12).map { f.stamps[it].toInt() })
        assertEquals(6,f.values.nextStamp.toInt())
        repeat(500) { f.core.set(it%24,it) }
        assertTrue(f.values.nextStamp.toInt() in 2..127)
        assertTrue(f.stamps.all { it.toInt() in 0..126 })
        val fields=IntArray(32); val values=CalendarStateCore.Values<Any>()
        val partial=CalendarStateCore(fields,ByteArray(24),values,f,true)
        assertFailsWith<IndexOutOfBoundsException> { partial.set(30,7) }
        assertEquals(7,fields[30]); assertEquals(3,values.nextStamp.toInt())
    }

    @Test fun boundsDatesConfigurationAndTemporalAccessors() {
        val f=Fixture(); f.values.lenient=false
        assertFailsWith<IllegalArgumentException> { f.core.setTimeInMillis(Long.MAX_VALUE) }
        assertEquals(0L,f.values.time)
        f.values.lenient=true; f.core.setTimeInMillis(Long.MIN_VALUE)
        assertEquals(Grego.MIN_MILLIS,f.values.time)
        f.core.setTimeInMillis(123)
        val date=f.core.getTime(); date.setTime(0); assertEquals(123L,f.core.getTime().getTime())
        f.core.setTime(Date(999)); assertEquals(999L,f.values.time)
        assertFailsWith<NullPointerException> { f.core.setTime(null) }
        val zone=Any(); f.core.setTimeZone(zone); assertSame(zone,f.core.getTimeZone()); assertTrue(f.values.isTimeSet)
        f.core.setTimeZone(null); assertNull(f.core.getTimeZone())
        f.values.areFieldsSet=true; f.core.setFirstDayOfWeek(0); assertTrue(f.values.areFieldsSet)
        assertFailsWith<IllegalArgumentException> { f.core.setFirstDayOfWeek(8) }
        f.core.setFirstDayOfWeek(1); assertFalse(f.values.areFieldsSet)
        f.core.setMinimalDaysInFirstWeek(Int.MAX_VALUE); assertEquals(7,f.core.getMinimalDaysInFirstWeek())
        assertFailsWith<IllegalArgumentException> { f.core.setRepeatedWallTimeOption(2) }
        assertFailsWith<IllegalArgumentException> { f.core.setSkippedWallTimeOption(3) }
        f.values.areFieldsSet=true; f.fields[2]=11; f.fields[22]=0
        assertEquals("M12",f.core.getTemporalMonthCode())
        f.fields[2]=-1; f.fields[22]=1
        assertFailsWith<AssertionError> { f.core.getTemporalMonthCode() }
        f.fields[22]=0; assertFailsWith<IndexOutOfBoundsException> { f.core.getTemporalMonthCode() }
        f.core.setTemporalMonthCode("M02"); assertEquals(1,f.fields[2]); assertEquals(0,f.fields[22])
        assertFailsWith<IllegalArgumentException> { f.core.setTemporalMonthCode("M13") }
        assertFailsWith<NullPointerException> { f.core.setTemporalMonthCode(null) }
        assertTrue(f.core.inTemporalLeapYear()); f.maximum=365; assertFalse(f.core.inTemporalLeapYear())
    }

    @Test fun storageAllocationContractAndExtendedMask() {
        for (size in listOf(0,23,33)) assertFailsWith<IllegalStateException> { CalendarStateCore.createStorage(IntArray(size)) }
        assertFailsWith<IllegalStateException> { CalendarStateCore.createStorage(null) }
        val fields=IntArray(32) { 777 }; val storage=CalendarStateCore.createStorage(fields)
        assertSame(fields,storage.fields); assertEquals(777,storage.fields[0]); assertTrue(storage.stamps.all { it.toInt()==0 })
        assertTrue(storage.internalSetMask and (1 shl 31) != 0)
        assertEquals(0,storage.internalSetMask and (1 shl 7))
    }
}
