package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.Grego
import io.github.heapy.icu4k.util.Date
import kotlin.test.*

class GregorianCalendarCoreTest {
    private class State(val fields: IntArray = IntArray(24))
    private class Fixture : GregorianCalendarCore.Owner<State> {
        val state = State()
        val trace = ArrayList<String>()
        val core = GregorianCalendarCore(this)
        var gregorian = intArrayOf(1970, 0, 1, 5, 1, 0)
        var newer = 19
        var cutoverYear = 1582
        var failCutover = false
        var usesDst = false
        val days = ArrayDeque<Pair<Boolean, Int>>()
        override fun cutoverExtendedYear(date: Date): Int { trace.add("cutover"); if (failCutover) error("cutover failed"); return cutoverYear }
        override fun isLeapYear(year: Int): Boolean { trace.add("leap:$year"); return core.isLeapYear(year) }
        override fun getGregorianMonth() = gregorian[1]
        override fun getGregorianDayOfMonth() = gregorian[2]
        override fun getGregorianDayOfYear() = gregorian[4]
        override fun getGregorianYear() = gregorian[0]
        override fun internalSet(field: Int, value: Int) { trace.add("set:$field:$value"); state.fields[field] = value }
        override fun internalGet(field: Int) = state.fields[field]
        override fun internalGet(field: Int, defaultValue: Int): Int { trace.add("get:$field:$defaultValue"); return if (state.fields[field] == 0) defaultValue else state.fields[field] }
        override fun internalGetMonth() = state.fields[2]
        override fun newerField(defaultField: Int, alternateField: Int): Int { trace.add("newer:$defaultField:$alternateField"); return newer }
        override fun baseComputeJulianDay(bestField: Int): Int {
            trace.add("base:$bestField:${core.invertGregorian}")
            val (gregorian, day) = days.removeFirst(); core.isGregorian = gregorian; return day
        }
        override fun get(field: Int) = state.fields[field]
        override fun set(field: Int, value: Int) { trace.add("publicSet:$field:$value"); state.fields[field] = value }
        override fun getYearLength(year: Int) = core.handleGetYearLength(year)
        override fun getFirstDayOfWeek() = 2
        override fun getMinimalDaysInFirstWeek() = 4
        override fun weekNumber(dayOfPeriod: Int, dayOfWeek: Int): Int { trace.add("week:$dayOfPeriod:$dayOfWeek"); return 52 }
        override fun baseRoll(field: Int, amount: Int) { trace.add("roll:$field:$amount") }
        override fun getMinimum(field: Int): Int { trace.add("minimum:$field"); return 7 }
        override fun baseGetActualMaximum(field: Int): Int { trace.add("maximum:$field"); return 31 }
        override fun cloneCalendar() = State(state.fields.copyOf())
        override fun setLenient(calendar: State, value: Boolean) { trace.add("lenient:$value") }
        override fun get(calendar: State, field: Int) = calendar.fields[field]
        override fun set(calendar: State, field: Int, value: Int) { calendar.fields[field] = value }
        override fun getTime(calendar: State) = Date(0)
        override fun setTime(calendar: State, date: Date) { trace.add("restore") }
        override fun zoneUsesDaylightTime(): Boolean { trace.add("uses"); return usesDst }
        override fun complete() { trace.add("complete") }
        fun compute(day: Int): List<Int> {
            gregorian = Grego.timeToFields((day.toLong() - 2440588) * 86400000, null)
            core.handleComputeFields(day)
            return listOf(0,1,2,5,6,19,23).map { state.fields[it] }
        }
    }

    @Test fun hybridFieldsMonthNormalizationAndLeapRules() {
        val f = Fixture()
        assertTrue(f.core.isLeapYear(1500)); assertFalse(f.core.isLeapYear(1700)); assertTrue(f.core.isLeapYear(2000))
        assertEquals(29, f.core.handleGetMonthLength(1500, 1))
        assertEquals(28, f.core.handleGetMonthLength(1700, 1))
        assertEquals(2440587, f.core.handleComputeMonthStart(1970, 0, true))
        assertEquals(2440587, f.core.handleComputeMonthStart(1969, 12, true))
        assertEquals(2440556, f.core.handleComputeMonthStart(1970, -1, false))
        assertEquals(listOf(1,1582,9,4,277,1582,9), f.compute(2299160))
        assertEquals(listOf(1,1582,9,15,288,1582,9), f.compute(2299161))
        assertEquals(listOf(1,1970,0,1,1,1970,0), f.compute(2440588))
        assertEquals(listOf(0,1,11,31,366,0,11), f.compute(1721423))
        assertEquals(5838270, f.core.handleGetLimit(1, 3))
        assertFailsWith<IndexOutOfBoundsException> { f.core.handleGetLimit(4, 0) }
        assertFailsWith<IndexOutOfBoundsException> { f.core.handleGetLimit(-1, 0) }
    }

    @Test fun cutoverSetterEpochDayQuirkFailuresAndIndependentCopies() {
        val f = Fixture()
        val original = f.core.getGregorianChange().getTime()
        val date = f.core.getGregorianChange(); date.setTime(0)
        assertEquals(original, f.core.getGregorianChange().getTime())
        assertFailsWith<NullPointerException> { f.core.setGregorianChange(null) }
        assertTrue(f.trace.isEmpty())
        f.core.setGregorianChange(Date(original))
        assertEquals(listOf(1,1582,9,14,287,1582,9), f.compute(2299160))
        val copy = f.core.copyFor(f)
        assertTrue(copy.hasSameCutover(f.core))
        f.core.setGregorianChange(Date(Long.MIN_VALUE))
        assertFalse(f.core.isLeapYear(1500)); assertTrue(copy.isLeapYear(1500))
        assertFalse(copy.hasSameCutover(f.core))
        f.core.setGregorianChange(Date(Long.MAX_VALUE))
        assertTrue(f.core.isLeapYear(1700))
        assertEquals(listOf(1,1969,11,19,353,1969,11), f.compute(2440588))
        val failing = Fixture(); failing.failCutover = true
        assertFailsWith<IllegalStateException> { failing.core.setGregorianChange(Date(0)) }
        assertEquals(0, failing.core.getGregorianChange().getTime())
        assertTrue(failing.core.isLeapYear(1500)) // Cutover year remains unchanged after failure.
        assertEquals(listOf(1,1582,9,14,287,1582,9), failing.compute(2299160))
        assertEquals(123 xor 0, failing.core.hashCode(123))
    }

    @Test fun extendedYearRetryAndDaylightShortCircuits() {
        val f = Fixture()
        assertEquals(1970, f.core.handleGetExtendedYear())
        assertEquals(listOf("newer:19:1", "get:19:1970"), f.trace)
        f.trace.clear(); f.newer = 1; f.state.fields[0] = 2; f.state.fields[1] = 7
        assertEquals(7, f.core.handleGetExtendedYear())
        f.state.fields[0] = 0 // This fixture's default getter models zero as unset; BC is checked by the JVM oracle.
        f.days.addAll(listOf(false to 2299161, true to 2299160))
        f.trace.clear(); f.core.invertGregorian = true
        assertEquals(2299160, f.core.handleComputeJulianDay(5))
        assertEquals(listOf("base:5:false", "base:5:true"), f.trace)
        assertTrue(f.core.invertGregorian)
        f.trace.clear()
        assertFalse(f.core.inDaylightTime()); assertEquals(listOf("uses"), f.trace)
        f.trace.clear(); f.usesDst = true; f.state.fields[16] = -1
        assertTrue(f.core.inDaylightTime()); assertEquals(listOf("uses", "complete"), f.trace)
    }

    @Test fun weekRollUsesISOYearAndBaseFallbacks() {
        val f = Fixture()
        f.state.fields[3] = 52; f.state.fields[17] = 2020; f.state.fields[6] = 1; f.state.fields[2] = 0; f.state.fields[7] = 5
        f.core.roll(3, 2)
        assertEquals(2, f.state.fields[3]); assertEquals(2020, f.state.fields[1])
        assertEquals(listOf("leap:2020", "leap:2020", "week:359:3", "publicSet:3:2", "publicSet:1:2020"), f.trace)
        f.trace.clear(); f.core.roll(2, -1)
        assertEquals(listOf("roll:2:-1"), f.trace)
        assertEquals(7, f.core.getActualMinimum(5)); assertEquals(31, f.core.getActualMaximum(5))
    }
}
