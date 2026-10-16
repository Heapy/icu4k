package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.util.Calendar
import com.ibm.icu.util.GregorianCalendar
import com.ibm.icu.util.TimeZone
import com.ibm.icu.util.ULocale
import io.github.heapy.icu4k.impl.Grego
import io.github.heapy.icu4k.util.Date
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import kotlin.test.*

class GregorianCalendarCoreOracleTest {
    private class Probe(zoneID: String = "Etc/GMT") : GregorianCalendar(TimeZone.getTimeZone(zoneID), ULocale.US) {
        var common = false
        private var ready = false
        var trace = ArrayList<String>()
        var port = GregorianCalendarCore<GregorianCalendar>(Binding(this))
        init { ready = true }
        private fun record(value: String) { if (ready) trace.add(value) }
        override fun isLeapYear(year: Int): Boolean {
            record("leap:$year")
            return if (common) port.isLeapYear(year) else super.isLeapYear(year)
        }
        override fun handleGetLimit(field: Int, limitType: Int) = if (common) port.handleGetLimit(field, limitType) else super.handleGetLimit(field, limitType)
        override fun handleGetMonthLength(year: Int, month: Int) = if (common) port.handleGetMonthLength(year, month) else super.handleGetMonthLength(year, month)
        override fun handleGetYearLength(year: Int): Int {
            record("length:$year")
            return if (common) port.handleGetYearLength(year) else super.handleGetYearLength(year)
        }
        override fun handleComputeFields(day: Int) { if (common) port.handleComputeFields(day) else super.handleComputeFields(day) }
        override fun handleGetExtendedYear(): Int = if (common) port.handleGetExtendedYear() else super.handleGetExtendedYear()
        override fun handleComputeJulianDay(bestField: Int): Int = if (common) port.handleComputeJulianDay(bestField) else super.handleComputeJulianDay(bestField)
        override fun handleComputeMonthStart(year: Int, month: Int, useMonth: Boolean): Int {
            record("start:$year:$month:$useMonth")
            return if (common) port.handleComputeMonthStart(year, month, useMonth) else super.handleComputeMonthStart(year, month, useMonth)
        }
        override fun roll(field: Int, amount: Int) { if (common) port.roll(field, amount) else super.roll(field, amount) }
        override fun getActualMinimum(field: Int) = if (common) port.getActualMinimum(field) else super.getActualMinimum(field)
        override fun getActualMaximum(field: Int) = if (common) port.getActualMaximum(field) else super.getActualMaximum(field)
        public override fun clone(): Probe {
            record("clone")
            val copy = super.clone() as Probe
            copy.port = port.copyFor(Binding(copy))
            return copy
        }
        fun change(date: Date?) { super.setGregorianChange(date); port.setGregorianChange(date) }
        fun limit(field: Int, type: Int) = handleGetLimit(field, type)
        fun monthLength(year: Int, month: Int) = handleGetMonthLength(year, month)
        fun yearLength(year: Int) = handleGetYearLength(year)
        fun monthStart(year: Int, month: Int, useMonth: Boolean) = handleComputeMonthStart(year, month, useMonth)
        fun fields(day: Int): List<Int> { computeGregorianFields(day); handleComputeFields(day); return listOf(ERA,YEAR,MONTH,DAY_OF_MONTH,DAY_OF_YEAR,EXTENDED_YEAR,ORDINAL_MONTH).map { internalGet(it) } }
        fun extendedYear() = handleGetExtendedYear()
        fun julianDay(field: Int) = handleComputeJulianDay(field)
        fun read(field: Int) = internalGet(field)
        fun read(field: Int, default: Int) = internalGet(field, default)
        fun write(field: Int, value: Int) = internalSet(field, value)
        fun month() = internalGetMonth()
        fun newer(a: Int, b: Int) = newerField(a, b)
        fun gregorianMonth() = getGregorianMonth()
        fun gregorianDayOfMonth() = getGregorianDayOfMonth()
        fun gregorianDayOfYear() = getGregorianDayOfYear()
        fun gregorianYear() = getGregorianYear()
        fun week(day: Int, dow: Int) = weekNumber(day, dow)
        fun finish() = complete()
        fun flags() = if (common) listOf(port.isGregorian, port.invertGregorian) else listOf(isGregorian, invertGregorian)
    }
    private class Binding(private val p: Probe) : GregorianCalendarCore.Owner<GregorianCalendar> {
        override fun cutoverExtendedYear(date: Date): Int = GregorianCalendar(p.timeZone).apply { time = date }.get(Calendar.EXTENDED_YEAR)
        override fun isLeapYear(year: Int) = p.isLeapYear(year)
        override fun getGregorianMonth() = p.gregorianMonth()
        override fun getGregorianDayOfMonth() = p.gregorianDayOfMonth()
        override fun getGregorianDayOfYear() = p.gregorianDayOfYear()
        override fun getGregorianYear() = p.gregorianYear()
        override fun internalSet(field: Int, value: Int) = p.write(field, value)
        override fun internalGet(field: Int) = p.read(field)
        override fun internalGet(field: Int, defaultValue: Int) = p.read(field, defaultValue)
        override fun internalGetMonth() = p.month()
        override fun newerField(defaultField: Int, alternateField: Int) = p.newer(defaultField, alternateField)
        override fun baseComputeJulianDay(bestField: Int): Int = baseJulian.invokeWithArguments(p, bestField) as Int
        override fun get(field: Int) = p.get(field)
        override fun set(field: Int, value: Int) = p.set(field, value)
        override fun getYearLength(year: Int) = p.yearLength(year)
        override fun getFirstDayOfWeek() = p.firstDayOfWeek
        override fun getMinimalDaysInFirstWeek() = p.minimalDaysInFirstWeek
        override fun weekNumber(dayOfPeriod: Int, dayOfWeek: Int) = p.week(dayOfPeriod, dayOfWeek)
        override fun baseRoll(field: Int, amount: Int) { baseRollHandle.invokeWithArguments(p, field, amount) }
        override fun getMinimum(field: Int) = p.getMinimum(field)
        override fun baseGetActualMaximum(field: Int): Int = baseActualMaximum.invokeWithArguments(p, field) as Int
        override fun cloneCalendar(): GregorianCalendar = p.clone()
        override fun setLenient(calendar: GregorianCalendar, value: Boolean) { calendar.isLenient = value }
        override fun get(calendar: GregorianCalendar, field: Int) = calendar.get(field)
        override fun set(calendar: GregorianCalendar, field: Int, value: Int) = calendar.set(field, value)
        override fun getTime(calendar: GregorianCalendar): Date = calendar.time
        override fun setTime(calendar: GregorianCalendar, date: Date) { calendar.time = date }
        override fun zoneUsesDaylightTime() = p.timeZone.useDaylightTime()
        override fun complete() = p.finish()
    }

    private fun compare(p: Probe, setup: () -> Unit = {}, action: () -> Any?) {
        p.common = false; setup(); p.trace.clear()
        val expected = outcome(action); val trace = p.trace.toList()
        p.common = true; setup(); p.trace.clear()
        assertEquals(expected, outcome(action))
        assertEquals(trace, p.trace)
    }

    @Test fun limitsMonthArithmeticLeapYearsAndFieldConversion() {
        val p = Probe()
        for (field in -1..24) for (limit in -1..4) compare(p) { p.limit(field, limit) }
        val years = listOf(Int.MIN_VALUE, -5838269, -400, -100, -4, -1, 0, 1, 4, 100, 400, 1500, 1582, 1600, 1700, 1900, 1970, 2000, 2100, 5838270, Int.MAX_VALUE)
        val months = listOf(Int.MIN_VALUE, -25, -13, -12, -1, 0, 1, 2, 11, 12, 13, 24, Int.MAX_VALUE)
        for (cutover in listOf(null, Long.MIN_VALUE, Grego.MIN_MILLIS, Grego.MIN_MILLIS + 1, -12219292800000L, -1L, 0L, 1582934400000L, Grego.MAX_MILLIS - 1, Grego.MAX_MILLIS, Long.MAX_VALUE)) {
            p.common = false
            if (cutover != null) p.change(Date(cutover))
            for (year in years) {
                compare(p) { p.isLeapYear(year) }
                compare(p) { p.yearLength(year) }
                for (month in months) {
                    compare(p) { p.monthLength(year, month) }
                    for (useMonth in listOf(false, true)) compare(p) { listOf(p.monthStart(year, month, useMonth), p.flags()) }
                }
            }
            for (day in listOf(Int.MIN_VALUE, -2130706432, -1, 0, 1721423, 1721424, 1721425, 1721426, 2299159, 2299160, 2299161, 2299162, 2440588, 2451604, 2130706432, Int.MAX_VALUE)) {
                compare(p, { p.clear() }) { p.fields(day) }
            }
        }
    }

    @Test fun fieldPrecedenceAndCutoverRetryThroughRealCalendarBase() {
        val p = Probe()
        for (cutover in listOf(null, Long.MIN_VALUE, -12219292800000L, 0L, Long.MAX_VALUE)) {
            p.common = false; if (cutover != null) p.change(Date(cutover))
            for (year in listOf(-400, 0, 1, 1500, 1582, 1583, 1900, 2000)) for (month in listOf(-1, 0, 1, 9, 11, 12)) for (day in listOf(-1, 1, 4, 5, 14, 15, 28, 32)) {
                compare(p, { p.clear(); p.set(Calendar.EXTENDED_YEAR, year); p.set(Calendar.MONTH, month); p.set(Calendar.DAY_OF_MONTH, day) }) {
                    listOf(p.julianDay(Calendar.DAY_OF_MONTH), p.flags())
                }
            }
        }
        val orders = listOf(emptyList(), listOf(19 to 7), listOf(1 to 7), listOf(0 to 0), listOf(1 to Int.MIN_VALUE, 0 to 0), listOf(19 to 9, 1 to 4), listOf(1 to 4, 19 to 9))
        for (fields in orders) compare(p, { p.clear(); for ((field,value) in fields) p.set(field,value) }) { p.extendedYear() }
    }

    @Test fun weekRollAndActualLimitsThroughRealCalendarState() {
        for (zone in listOf("Etc/GMT", "America/New_York", "Pacific/Apia")) {
            val p = Probe(zone)
            for (date in listOf(-12219292800000L, -2208988800000L, 1451520000000L, 1483228800000L, 1609372800000L, 1710054000000L)) {
                for (first in listOf(1, 2, 7)) for (minimal in listOf(1, 4, 7)) for (amount in listOf(Int.MIN_VALUE, -104, -53, -1, 0, 1, 53, 104, Int.MAX_VALUE)) {
                    compare(p, { p.timeInMillis = date; p.firstDayOfWeek = first; p.minimalDaysInFirstWeek = minimal }) {
                        p.roll(Calendar.WEEK_OF_YEAR, amount)
                        listOf(p.timeInMillis, p.get(Calendar.YEAR), p.get(Calendar.WEEK_OF_YEAR))
                    }
                }
                for (field in listOf(Calendar.ERA, Calendar.YEAR, Calendar.MONTH, Calendar.DAY_OF_MONTH, Calendar.DAY_OF_YEAR, Calendar.WEEK_OF_YEAR)) {
                    compare(p, { p.timeInMillis = date }) { p.getActualMinimum(field) }
                    compare(p, { p.timeInMillis = date }) { p.getActualMaximum(field) }
                }
                compare(p, { p.timeInMillis = date }) { p.roll(Calendar.MONTH, 1); p.timeInMillis }
            }
        }
    }

    companion object {
        private val lookup = MethodHandles.privateLookupIn(GregorianCalendar::class.java, MethodHandles.lookup())
        private val intType = Int::class.javaPrimitiveType!!
        private val baseJulian = lookup.findSpecial(Calendar::class.java, "handleComputeJulianDay", MethodType.methodType(intType, intType), GregorianCalendar::class.java)
        private val baseRollHandle = lookup.findSpecial(Calendar::class.java, "roll", MethodType.methodType(Void.TYPE, intType, intType), GregorianCalendar::class.java)
        private val baseActualMaximum = lookup.findSpecial(Calendar::class.java, "getActualMaximum", MethodType.methodType(intType, intType), GregorianCalendar::class.java)
        private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
            when (e) {
                is VirtualMachineError -> throw e
                is IndexOutOfBoundsException -> "bounds"
                is NullPointerException -> "null"
                else -> e.javaClass.simpleName + ":" + e.message
            }
        }
    }
}
