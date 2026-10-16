package io.github.heapy.icu4k.util

import com.ibm.icu.util.Calendar as SourceCalendar
import com.ibm.icu.util.GregorianCalendar as Source
import com.ibm.icu.util.ULocale as SourceLocale
import kotlin.random.Random
import kotlin.test.*

class GregorianCalendarOracleTest {
    private class SourceFixed(private var raw: Int) : com.ibm.icu.util.TimeZone("custom") {
        override fun getRawOffset() = raw
        override fun setRawOffset(offsetMillis: Int) { raw = offsetMillis }
        override fun getOffset(era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, milliseconds: Int) = raw
        override fun useDaylightTime() = false
        override fun inDaylightTime(date: java.util.Date?) = false
    }
    private class Fixed(private var raw: Int) : TimeZone("custom") {
        override fun getRawOffset() = raw
        override fun setRawOffset(offsetMillis: Int) { raw = offsetMillis }
        override fun getOffset(era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, milliseconds: Int) = raw
        override fun useDaylightTime() = false
        override fun inDaylightTime(date: Date?) = false
        override fun cloneAsThawed(): TimeZone = Fixed(raw).also { it.setID(getID()) }
    }
    private fun pair(locale: String = "en_US", offset: Int = 0): Pair<Source, GregorianCalendar> =
        Source(SourceFixed(offset), SourceLocale(locale)) to GregorianCalendar(Fixed(offset), ULocale(locale))
    private fun snapshot(calendar: SourceCalendar) = List(calendar.fieldCount) { calendar.get(it) }
    private fun snapshot(calendar: Calendar) = List(calendar.getFieldCount()) { calendar.get(it) }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (failure: Throwable) {
        val cause = if (failure is java.lang.reflect.InvocationTargetException) failure.cause!! else failure
        if (cause is VirtualMachineError) throw cause
        when (cause) { is IndexOutOfBoundsException -> "bounds"; is NullPointerException -> "null"; else -> cause.javaClass.name + ":" + cause.message }
    }
    @Test fun constructorCallbacksAndCloneAvoidReinitializationMatchSource() {
        val left = arrayListOf<String>(); val right = arrayListOf<String>()
        val source = object : Source(SourceFixed(0), SourceLocale("en_US@fw=mon")) {
            override fun setWeekData(data: SourceCalendar.WeekData?): SourceCalendar { left.add("week"); return super.setWeekData(data) }
            override fun setFirstDayOfWeek(value: Int) { left.add("first:$value"); super.setFirstDayOfWeek(value) }
            override fun setMinimalDaysInFirstWeek(value: Int) { left.add("minimal:$value"); super.setMinimalDaysInFirstWeek(value) }
            override fun handleCreateFields(): IntArray { left.add("fields"); return super.handleCreateFields() }
            override fun setTimeInMillis(value: Long) { left.add("time"); super.setTimeInMillis(value) }
        }
        val port = object : GregorianCalendar(Fixed(0), ULocale("en_US@fw=mon")) {
            override fun setWeekData(data: Calendar.WeekData?): Calendar { right.add("week"); return super.setWeekData(data) }
            override fun setFirstDayOfWeek(value: Int) { right.add("first:$value"); super.setFirstDayOfWeek(value) }
            override fun setMinimalDaysInFirstWeek(value: Int) { right.add("minimal:$value"); super.setMinimalDaysInFirstWeek(value) }
            override fun handleCreateFields(): IntArray? { right.add("fields"); return super.handleCreateFields() }
            override fun setTimeInMillis(value: Long) { right.add("time"); super.setTimeInMillis(value) }
        }
        assertEquals(left, right)
        assertEquals(listOf("week", "first:1", "minimal:1", "first:2", "fields", "time"), right)
        left.clear(); right.clear(); source.clone(); port.clone()
        assertTrue(left.isEmpty()); assertTrue(right.isEmpty())
    }
    @Test fun completePublicLifecycleMatchesSourceAcrossLocalesAndInstants() {
        val random = Random(783)
        val times = listOf(Long.MIN_VALUE, Long.MAX_VALUE, -12219292800001L, -12219292800000L, -1L, 0L, 1719792000000L) + List(80) { random.nextLong(-100000000000000L, 100000000000000L) }
        for (locale in listOf("en_US", "en_GB", "ar_SA", "en_US_POSIX@fw=mon;calendar=gregorian;hours=h23")) {
            val (source, port) = pair(locale, 19800000)
            assertEquals(source.firstDayOfWeek, port.getFirstDayOfWeek()); assertEquals(source.minimalDaysInFirstWeek, port.getMinimalDaysInFirstWeek())
            assertEquals(source.getLocale(null).toString(), port.getLocale(null).toString())
            assertEquals(source.weekData.toString(), port.getWeekData().toString())
            for (time in times) {
                source.timeInMillis = time; port.setTimeInMillis(time)
                assertEquals(source.timeInMillis, port.getTimeInMillis())
                assertEquals(snapshot(source), snapshot(port), "$locale/$time")
                assertEquals(source.isWeekend, port.isWeekend())
            }
        }
    }
    @Test fun fieldPrecedenceArithmeticLimitsAndCloneStateMatchSource() {
        val (source, port) = pair("en_GB")
        val random = Random(1783)
        repeat(70) {
            source.clear(); port.clear()
            val year = random.nextInt(1400, 2400); val month = random.nextInt(-3, 15); val day = random.nextInt(1, 36)
            source.set(year, month, day, 23, 59, 58); port.set(year, month, day, 23, 59, 58)
            if (it % 3 == 0) { source.set(Calendar.DAY_OF_YEAR, 80); port.set(Calendar.DAY_OF_YEAR, 80) }
            assertEquals(source.timeInMillis, port.getTimeInMillis()); assertEquals(snapshot(source), snapshot(port))
            for (field in listOf(Calendar.YEAR, Calendar.MONTH, Calendar.DATE, Calendar.WEEK_OF_YEAR, Calendar.DAY_OF_YEAR)) {
                val a = source.clone(); val b = port.clone()
                assertEquals(a.getActualMaximum(field), b.getActualMaximum(field), "maximum/$field/$year")
                a.add(field, 3); b.add(field, 3); assertEquals(snapshot(a), snapshot(b), "add/$field/$year")
                a.roll(field, -2); b.roll(field, -2); assertEquals(snapshot(a), snapshot(b), "roll/$field/$year")
            }
        }
        source.clear(); port.clear(); source.isLenient = false; port.setLenient(false)
        source.set(2023, 1, 29); port.set(2023, 1, 29)
        assertEquals(outcome { source.timeInMillis }, outcome { port.getTimeInMillis() })
    }
    @Test fun cutoverChangesAndClonesRetainPinnedBehavior() {
        val (source, port) = pair()
        for (cutover in listOf(-12219292800000L, 0L, -1000000000000L, Long.MIN_VALUE, Long.MAX_VALUE)) {
            source.setGregorianChange(Date(cutover)); port.setGregorianChange(Date(cutover))
            for (date in listOf(-12219292800001L, -1L, 0L, 1719792000000L)) {
                source.timeInMillis = date; port.setTimeInMillis(date)
                assertEquals(snapshot(source), snapshot(port), "$cutover/$date")
                assertEquals(source.hashCode(), port.hashCode())
                assertEquals(snapshot(source.clone()), snapshot(port.clone()))
            }
        }
    }
    @Test fun nullComparisonComputesTheReceiverBeforeDereferencingTheArgument() {
        val (source, port) = pair()
        source.clear(); port.clear(); source.isLenient = false; port.setLenient(false)
        source.set(2023, 1, 29); port.set(2023, 1, 29)
        val sourceCall = SourceCalendar::class.java.getMethod("compareTo", SourceCalendar::class.java)
        val portCall = Calendar::class.java.getMethod("compareTo", Calendar::class.java)
        assertEquals(outcome { sourceCall.invoke(source, null) }, outcome { portCall.invoke(port, null) })
    }
    @Test fun superclassCallbacksObserveZeroGregorianStateAndKeepFlagMutations() {
        val left = arrayListOf<String>(); val right = arrayListOf<String>()
        val source = object : Source(SourceFixed(0), SourceLocale.US) {
            override fun setWeekData(data: SourceCalendar.WeekData?): SourceCalendar {
                left.add("${gregorianChange.time}:${isLeapYear(1500)}:$isGregorian:$invertGregorian")
                isGregorian = true; invertGregorian = true
                return super.setWeekData(data)
            }
            fun flags() = listOf(isGregorian, invertGregorian)
        }
        val port = object : GregorianCalendar(Fixed(0), ULocale.US) {
            override fun setWeekData(data: Calendar.WeekData?): Calendar {
                right.add("${getGregorianChange().getTime()}:${isLeapYear(1500)}:$isGregorian:$invertGregorian")
                isGregorian = true; invertGregorian = true
                return super.setWeekData(data)
            }
            fun flags() = listOf(isGregorian, invertGregorian)
        }
        assertEquals(left, right); assertEquals(listOf("0:false:false:false"), right)
        assertEquals(source.gregorianChange.time, port.getGregorianChange().getTime())
        assertEquals(source.isLeapYear(1500), port.isLeapYear(1500))
        assertEquals(source.flags(), port.flags()); assertEquals(listOf(true, true), port.flags())
    }
}
