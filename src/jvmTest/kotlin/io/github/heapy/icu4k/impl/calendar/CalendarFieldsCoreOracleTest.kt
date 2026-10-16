@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.calendar.GregorianCalendarCoreOracleTest.Probe
import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

class CalendarFieldsCoreOracleTest {
    private fun compare(p: Probe, setup: () -> Unit = {}, action: () -> Any?) {
        p.common = false; setup(); p.trace.clear()
        val expected = outcome(action); val trace = p.trace.toList()
        p.common = true; setup(); p.trace.clear()
        assertEquals(expected, outcome(action))
        assertEquals(trace, p.trace)
    }
    private fun snapshot(p: Probe): List<Any> = listOf(p.rawFields().toList(), p.rawStamps().toList(),
        listOf(p.gregorianYear(),p.gregorianMonth(),p.gregorianDayOfMonth(),p.gregorianDayOfYear()))

    @Test fun fullCommonCalendarFieldPipelineAcrossZonesDatesAndWeekRules() {
        for (zone in listOf("Etc/GMT", "America/New_York", "Europe/London", "Australia/Lord_Howe", "Pacific/Apia", "Asia/Kathmandu", "Africa/Casablanca", "GMT+23:59:59")) {
            val p = Probe(zone)
            for (date in listOf(Long.MIN_VALUE,-184303902528000000L,-12219379200000L,-12219292800000L,-2208988800000L,-1L,0L,999L,86400000L,
                1451520000000L,1483228800000L,1609372800000L,1710053999999L,1710054000000L,1730613599999L,1730613600000L,183882168921600000L,Long.MAX_VALUE)) {
                for (first in 1..7) for (minimal in 1..7) compare(p, {
                    p.timeInMillis = date; p.firstDayOfWeek = first; p.minimalDaysInFirstWeek = minimal
                }) { p.computeAll(); snapshot(p) }
            }
        }
    }

    @Test fun hourPrecedenceStampQueriesAndIntVersusLongArithmetic() {
        val p = Probe()
        val random = Random(7803)
        repeat(2500) {
            val values = IntArray(24) { if (it in 9..14) random.nextInt() else 0 }
            val stamps = ByteArray(24) { listOf(-128,-1,0,1,2,3,127)[random.nextInt(7)].toByte() }
            compare(p, { values.copyInto(p.rawFields()); stamps.copyInto(p.rawStamps()) }) { listOf(p.millis32(),p.millis64()) }
            for ((a,b) in listOf(10 to 11, 1 to 19, 2 to 23, -1 to 0, 24 to 0, 0 to 24)) {
                compare(p) { p.chooseNewer(a,b) }
            }
            compare(p) { p.newest(0,23,-1) }
            compare(p) { p.newest(3,1,5) }
            compare(p) { p.stampAt(11) }
        }
        for (first in listOf(-1,0,23,24)) for (last in listOf(-1,0,23,24)) compare(p) { p.newest(first,last,0) }
    }

    @Test fun precedenceGroupsRemappingTiesAndMalformedTables() {
        val p = Probe(); val random = Random(32803)
        assertEquals(p.sourceDateTable().map { it.map(IntArray::toList) },CalendarFieldsCore.DATE_PRECEDENCE.map { it.map(IntArray::toList) })
        val tables: List<Array<Array<IntArray>>?> = listOf(null, emptyArray(), arrayOf(emptyArray()), arrayOf(arrayOf(intArrayOf())),
            arrayOf(arrayOf(intArrayOf(-1))), arrayOf(arrayOf(intArrayOf(24))), arrayOf(arrayOf(intArrayOf(63,1))),
            arrayOf(arrayOf(intArrayOf(37,1),intArrayOf(3,7))), CalendarFieldsCore.DATE_PRECEDENCE,
            CalendarFieldsCore.DOW_PRECEDENCE, CalendarFieldsCore.MONTH_PRECEDENCE,
            arrayOf(arrayOf(intArrayOf(3,7),intArrayOf(4,7)),arrayOf(intArrayOf(6))),
            arrayOf(arrayOf(intArrayOf(32),intArrayOf(5))))
        repeat(2000) {
            val stamps = ByteArray(24) { random.nextInt(-1,5).toByte() }
            for (table in tables) compare(p, { stamps.copyInto(p.rawStamps()) }) { p.resolve(table) }
        }
    }

    @Test fun standaloneWeekArithmeticAndYearBoundariesIncludingOverflow() {
        val p = Probe()
        for (first in 1..7) for (minimal in 1..7) {
            p.firstDayOfWeek = first; p.minimalDaysInFirstWeek = minimal
            for (day in listOf(Int.MIN_VALUE,-8,-1,0,1,2,6,7,8,28,31,359,365,366,Int.MAX_VALUE)) {
                for (dow in listOf(Int.MIN_VALUE,-1,0,1,2,7,8,Int.MAX_VALUE)) {
                    compare(p) { p.week3(day,day,dow) }
                    compare(p) { p.week3(day,31,dow) }
                }
            }
            for (year in listOf(Int.MIN_VALUE,-1,0,1,1582,1900,2000,2020,2021,Int.MAX_VALUE)) {
                for (doy in listOf(Int.MIN_VALUE,-1,0,1,2,359,365,366,367,Int.MAX_VALUE)) {
                    val values = IntArray(24).apply { this[19] = year; this[6] = doy; this[5] = doy; this[7] = 5 }
                    compare(p, { values.copyInto(p.rawFields()) }) { p.weekFields(); p.rawFields().toList() }
                }
            }
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
