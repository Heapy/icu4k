package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.calendar.GregorianCalendarCoreOracleTest.Probe
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.random.Random
import kotlin.test.*

class CalendarDateCoreOracleTest {
    private fun compare(p: Probe, setup: () -> Unit = {}, action: () -> Any?) {
        p.common = false; setup(); p.trace.clear()
        val expected = outcome(action); val fields = p.rawFields().toList(); val stamps = p.rawStamps().toList(); val trace = p.trace.toList()
        p.common = true; setup(); p.trace.clear()
        assertEquals(expected,outcome(action))
        assertEquals(fields,p.rawFields().toList()); assertEquals(stamps,p.rawStamps().toList()); assertEquals(trace,p.trace)
    }
    @Test fun fullDateResolutionAndJulianFrameworkIncludingOrdinalMonthsAndWeeks() {
        val p = Probe(); val random = Random(78319)
        repeat(2500) {
            val values = IntArray(24) { random.nextInt(-20,400) }
            values[19] = listOf(Int.MIN_VALUE,-400,0,1,1582,1900,2000,Int.MAX_VALUE)[random.nextInt(8)]
            val stamps = ByteArray(24) { random.nextInt(-1,6).toByte() }
            val first = random.nextInt(1,8); val minimal = random.nextInt(1,8)
            val setup: () -> Unit = { p.clear(); p.firstDayOfWeek = first; p.minimalDaysInFirstWeek = minimal; values.copyInto(p.rawFields()); stamps.copyInto(p.rawStamps()); Unit }
            compare(p,setup) { p.allJulian() }
            for (field in listOf(-1,3,4,5,6,8,18,24)) compare(p,setup) { p.baseJulianDay(field) }
            compare(p,setup) { listOf(p.month(),p.monthDefault(-7),p.read(19,1970)) }
        }
    }
    @Test fun validationMetadataAndPrivateFieldWrites() {
        val p = Probe()
        for (year in listOf(-400,0,1,1582,1900,2000)) for (value in listOf(Int.MIN_VALUE,-1,0,1,28,29,30,31,59,60,365,366,Int.MAX_VALUE)) {
            for (field in -1..24) {
                val setup = {
                    p.clear(); p.set(19,year); p.set(2,1)
                    if (field in 0..23) { p.rawFields()[field] = value; p.rawStamps()[field] = 2 }
                }
                compare(p,setup) { p.validateOne(field) }
                compare(p,setup) { p.validateAll() }
                compare(p,setup) { p.validateRange(field,-1,31) }
                compare(p,setup) { p.write(field,value) }
                compare(p) { p.nameOf(field) }
            }
        }
        for (field in listOf(31,32,33,63,Int.MIN_VALUE,Int.MAX_VALUE)) compare(p,{ p.clear() }) { p.write(field,123) }
    }
    @Test fun gregorianNormalizationDivisionRemaindersAndEpochConversions() {
        val p = Probe()
        val ints = listOf(Int.MIN_VALUE,-5838269,-400,-13,-1,0,1,4,12,100,400,1970,2000,5838270,Int.MAX_VALUE)
        for (year in ints) for (month in ints) {
            compare(p) { p.gregorianStart(year,month) }
            compare(p) { p.gregorianLength(year,month) }
            compare(p) { p.previousGregorianLength(year,month) }
        }
        for (n in ints) for (d in listOf(Int.MIN_VALUE,-400,-1,0,1,4,12,400,Int.MAX_VALUE)) {
            compare(p) { p.floorInt(n,d) }
            for (size in listOf(null,0,1,2)) compare(p) {
                val remainder = size?.let { IntArray(it) { 777 } }
                val result = outcome { p.floorIntRemainder(n,d,remainder) }
                listOf(result,remainder?.toList())
            }
        }
        for (n in listOf(Long.MIN_VALUE,Int.MIN_VALUE.toLong(),-86400001,-1,0,1,86400000,Int.MAX_VALUE.toLong(),Long.MAX_VALUE)) {
            compare(p) { p.millisDay(n) }
            for (d in listOf(Int.MIN_VALUE,-400,-1,0,1,12,400,Int.MAX_VALUE)) {
                compare(p) { p.floorLong(n,d.toLong()) }
                for (size in listOf(null,0,1,2)) compare(p) {
                    val remainder = size?.let { IntArray(it) { 777 } }
                    val result = outcome { p.floorLongRemainder(n,d,remainder) }
                    listOf(result,remainder?.toList())
                }
            }
        }
        for (day in ints) compare(p) { p.dayMillis(day) }
    }
    @Test fun defaultMonthAndYearLengthPreserveDispatchAndDiagnostic() {
        val p = Probe()
        val original = System.out
        try {
            for (year in listOf(Int.MIN_VALUE,-400,0,1,1500,1582,1900,2000,Int.MAX_VALUE)) for (month in listOf(Int.MIN_VALUE,-13,-1,0,1,11,12,Int.MAX_VALUE)) {
                var expectedOutput = ""
                for (common in listOf(false,true)) {
                    p.common = common
                    val output = ByteArrayOutputStream(); System.setOut(PrintStream(output,true,Charsets.UTF_8))
                    val result = outcome { p.baseMonthLength(year,month) }
                    if (!common) expectedOutput = "$result|${output.toString(Charsets.UTF_8)}"
                    else assertEquals(expectedOutput,"$result|${output.toString(Charsets.UTF_8)}")
                }
                compare(p) { p.baseYearLength(year) }
            }
        } finally { System.setOut(original) }
    }
    companion object {
        private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
            when (e) {
                is VirtualMachineError -> throw e
                is NullPointerException -> "null"
                is IndexOutOfBoundsException -> "bounds"
                else -> e.javaClass.simpleName + ":" + e.message
            }
        }
    }
}
