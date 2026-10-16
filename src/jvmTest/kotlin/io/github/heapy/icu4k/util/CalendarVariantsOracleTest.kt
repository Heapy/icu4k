@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util

import com.ibm.icu.util.Calendar as SourceCalendar
import com.ibm.icu.util.ULocale as SourceLocale
import kotlin.random.Random
import kotlin.test.*

class CalendarVariantsOracleTest {
    private fun pair(kind: Int, locale: String, raw: Int = 0): Pair<SourceCalendar, Calendar> {
        val left = com.ibm.icu.util.SimpleTimeZone(raw, "custom")
        val right = SimpleTimeZone(raw, "custom")
        val a = SourceLocale(locale); val b = ULocale(locale)
        return when (kind) {
            0 -> com.ibm.icu.util.BuddhistCalendar(left, a) to BuddhistCalendar(right, b)
            1 -> com.ibm.icu.util.TaiwanCalendar(left, a) to TaiwanCalendar(right, b)
            2 -> com.ibm.icu.util.PersianCalendar(left, a) to PersianCalendar(right, b)
            else -> com.ibm.icu.util.IndianCalendar(left, a) to IndianCalendar(right, b)
        }
    }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (failure: Throwable) {
        val cause = if (failure is java.lang.reflect.InvocationTargetException) failure.cause!! else failure
        if (cause is VirtualMachineError) throw cause
        when (cause) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> cause.javaClass.name + ":" + cause.message }
    }
    private fun snapshot(calendar: SourceCalendar): List<Any> = listOf(calendar.timeInMillis, List(calendar.fieldCount) { calendar.get(it) }, calendar.relatedYear, calendar.temporalMonthCode)
    private fun snapshot(calendar: Calendar): List<Any> = listOf(calendar.getTimeInMillis(), List(calendar.getFieldCount()) { calendar.get(it) }, calendar.getRelatedYear(), calendar.getTemporalMonthCode())
    @Test fun publicFieldsAndLocaleMetadataMatchPinnedSource() {
        val random = Random(78317)
        val times = listOf(Long.MIN_VALUE, Long.MAX_VALUE, -12219292800001L, -12219292800000L, -1830384000000L, -1L, 0L, 1710892800000L, 1710979200000L) + List(90) { random.nextLong(-100000000000000L, 100000000000000L) }
        for (kind in 0..3) for (locale in listOf("en_US", "th_TH", "fa_IR@fw=mon;calendar=hebrew")) for (raw in listOf(-12600000, 19800000)) {
            val (source, port) = pair(kind, locale, raw)
            assertEquals(source.type, port.getType()); assertEquals(source.getLocale(null).toString(), port.getLocale(null).toString())
            assertEquals(source.weekData.toString(), port.getWeekData().toString())
            for (time in times) {
                source.timeInMillis = time; port.setTimeInMillis(time)
                assertEquals(outcome { snapshot(source) }, outcome { snapshot(port) }, "$kind/$locale/$raw/$time")
            }
        }
    }
    @Test fun fieldStampPrecedenceLimitsAndArithmeticMatchPinnedSource() {
        val random = Random(78318)
        for (kind in 0..3) {
            val (source, port) = pair(kind, "en_GB")
            repeat(40) { iteration ->
                source.clear(); port.clear()
                val year = random.nextInt(1, 2800); val month = random.nextInt(-2, 14); val day = random.nextInt(1, 34)
                source.set(year, month, day, 12, 34, 56); port.set(year, month, day, 12, 34, 56)
                if (iteration % 3 == 0) { source.set(Calendar.DAY_OF_YEAR, 100); port.set(Calendar.DAY_OF_YEAR, 100) }
                assertEquals(outcome { snapshot(source) }, outcome { snapshot(port) }, "fields/$kind/$year/$month/$day")
                for (field in listOf(Calendar.YEAR, Calendar.MONTH, Calendar.DATE, Calendar.WEEK_OF_YEAR, Calendar.DAY_OF_YEAR)) {
                    val a = source.clone(); val b = port.clone()
                    assertEquals(outcome { a.getActualMaximum(field) }, outcome { b.getActualMaximum(field) }, "max/$kind/$field")
                    assertEquals(outcome { a.add(field, 2); snapshot(a) }, outcome { b.add(field, 2); snapshot(b) }, "add/$kind/$field")
                    assertEquals(outcome { a.roll(field, -1); snapshot(a) }, outcome { b.roll(field, -1); snapshot(b) }, "roll/$kind/$field")
                }
            }
        }
    }
    @Test fun cloneHashAndStrictValidationMatchPinnedSource() {
        for (kind in 0..3) {
            val (source, port) = pair(kind, "en_US", 12345)
            source.timeInMillis = 1719792000000L; port.setTimeInMillis(1719792000000L)
            source.get(Calendar.MONTH); port.get(Calendar.MONTH)
            val a = source.clone(); val b = port.clone()
            assertEquals(source.hashCode(), port.hashCode()); assertEquals(a.hashCode(), b.hashCode())
            assertEquals(source == a, port == b)
            a.add(Calendar.MONTH, 1); b.add(Calendar.MONTH, 1)
            assertEquals(source == a, port == b); assertEquals(snapshot(a), snapshot(b)); assertEquals(snapshot(source), snapshot(port))
            source.isLenient = false; port.setLenient(false); source.clear(); port.clear()
            source.set(1400, 12, 1); port.set(1400, 12, 1)
            assertEquals(outcome { source.timeInMillis }, outcome { port.getTimeInMillis() })
        }
    }
    @Test fun indianGregorianSupportRetainsVirtualDispatch() {
        val left = arrayListOf<String>(); val right = arrayListOf<String>()
        val source = object : com.ibm.icu.util.IndianCalendar(com.ibm.icu.util.SimpleTimeZone(0, "custom"), SourceLocale.US) {
            override fun computeGregorianMonthStart(year: Int, month: Int): Int { left.add("$year/$month"); return super.computeGregorianMonthStart(year, month) }
        }
        val port = object : IndianCalendar(SimpleTimeZone(0, "custom"), ULocale.US) {
            override fun computeGregorianMonthStart(year: Int, month: Int): Int { right.add("$year/$month"); return super.computeGregorianMonthStart(year, month) }
        }
        source.clear(); port.clear(); source.set(1946, 0, 1); port.set(1946, 0, 1)
        assertEquals(snapshot(source), snapshot(port)); assertEquals(left, right)
        assertTrue(right.isNotEmpty())
    }
    @Test fun constructorEraLimitsDoNotRequireAllocatedFields() {
        val left = arrayListOf<String>(); val right = arrayListOf<String>()
        fun record(calendar: SourceCalendar) { left.add("${calendar.type}:${calendar.getMinimum(Calendar.ERA)}:${calendar.getMaximum(Calendar.ERA)}:${calendar.getActualMaximum(Calendar.ERA)}") }
        fun record(calendar: Calendar) { right.add("${calendar.getType()}:${calendar.getMinimum(Calendar.ERA)}:${calendar.getMaximum(Calendar.ERA)}:${calendar.getActualMaximum(Calendar.ERA)}") }
        object : com.ibm.icu.util.BuddhistCalendar(com.ibm.icu.util.SimpleTimeZone(0, "custom"), SourceLocale.US) {
            override fun setWeekData(data: SourceCalendar.WeekData?): SourceCalendar { record(this); return super.setWeekData(data) }
        }
        object : BuddhistCalendar(SimpleTimeZone(0, "custom"), ULocale.US) {
            override fun setWeekData(data: Calendar.WeekData?): Calendar { record(this); return super.setWeekData(data) }
        }
        object : com.ibm.icu.util.TaiwanCalendar(com.ibm.icu.util.SimpleTimeZone(0, "custom"), SourceLocale.US) {
            override fun setWeekData(data: SourceCalendar.WeekData?): SourceCalendar { record(this); return super.setWeekData(data) }
        }
        object : TaiwanCalendar(SimpleTimeZone(0, "custom"), ULocale.US) {
            override fun setWeekData(data: Calendar.WeekData?): Calendar { record(this); return super.setWeekData(data) }
        }
        object : com.ibm.icu.util.PersianCalendar(com.ibm.icu.util.SimpleTimeZone(0, "custom"), SourceLocale.US) {
            override fun setWeekData(data: SourceCalendar.WeekData?): SourceCalendar { record(this); return super.setWeekData(data) }
        }
        object : PersianCalendar(SimpleTimeZone(0, "custom"), ULocale.US) {
            override fun setWeekData(data: Calendar.WeekData?): Calendar { record(this); return super.setWeekData(data) }
        }
        object : com.ibm.icu.util.IndianCalendar(com.ibm.icu.util.SimpleTimeZone(0, "custom"), SourceLocale.US) {
            override fun setWeekData(data: SourceCalendar.WeekData?): SourceCalendar { record(this); return super.setWeekData(data) }
        }
        object : IndianCalendar(SimpleTimeZone(0, "custom"), ULocale.US) {
            override fun setWeekData(data: Calendar.WeekData?): Calendar { record(this); return super.setWeekData(data) }
        }
        assertEquals(left, right)
        assertEquals(listOf("buddhist:0:0:0", "roc:0:1:1", "persian:0:0:0", "indian:0:0:0"), right)
    }
    @Test fun constructorScalarHelpersAndLaterFieldStorageMatchSource() {
        val left = arrayListOf<String>(); val right = arrayListOf<String>()
        val source = object : com.ibm.icu.util.IndianCalendar(com.ibm.icu.util.SimpleTimeZone(0, "custom"), SourceLocale.US) {
            override fun setWeekData(data: SourceCalendar.WeekData?): SourceCalendar {
                computeGregorianFields(2440588)
                left.add("${fieldName(Calendar.YEAR)}:${getGregorianYear()}:${getDefaultMonthInYear(1970)}:${getDefaultDayInMonth(1970, 0)}:${weekNumber(1, 1, Calendar.THURSDAY)}")
                left.add(outcome { internalSet(Calendar.YEAR, 1) }.toString())
                return super.setWeekData(data)
            }
            fun cachedYear() = getGregorianYear()
        }
        val port = object : IndianCalendar(SimpleTimeZone(0, "custom"), ULocale.US) {
            override fun setWeekData(data: Calendar.WeekData?): Calendar {
                computeGregorianFields(2440588)
                right.add("${fieldName(Calendar.YEAR)}:${getGregorianYear()}:${getDefaultMonthInYear(1970)}:${getDefaultDayInMonth(1970, 0)}:${weekNumber(1, 1, Calendar.THURSDAY)}")
                right.add(outcome { internalSet(Calendar.YEAR, 1) }.toString())
                return super.setWeekData(data)
            }
            fun cachedYear() = getGregorianYear()
        }
        assertEquals(left, right); assertEquals(1970, port.cachedYear()); assertEquals(source.cachedYear(), port.cachedYear())
        source.timeInMillis = 1719792000000L; port.setTimeInMillis(1719792000000L)
        assertEquals(snapshot(source), snapshot(port)); assertEquals(source.cachedYear(), port.cachedYear())
    }
}
