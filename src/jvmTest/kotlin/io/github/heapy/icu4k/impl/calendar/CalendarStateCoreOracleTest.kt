@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.util.TimeZone
import io.github.heapy.icu4k.impl.Grego
import io.github.heapy.icu4k.impl.calendar.GregorianCalendarCoreOracleTest.Probe
import io.github.heapy.icu4k.util.Date
import kotlin.random.Random
import kotlin.test.*

class CalendarStateCoreOracleTest {
    private fun pair(zone: String = "Etc/GMT"): Pair<Probe,Probe> {
        val a=Probe(zone); val b=Probe(zone); b.common=true
        a.timeInMillis=0; b.timeInMillis=0
        return a to b
    }
    private fun compare(a: Probe,b: Probe,action: (Probe) -> Any?) {
        a.trace.clear(); b.trace.clear()
        val expected=outcome { action(a) }; val actual=outcome { action(b) }
        assertEquals(expected,actual)
        assertEquals(a.rawFields().toList(),b.rawFields().toList())
        assertEquals(a.rawStamps().toList(),b.rawStamps().toList())
        assertEquals(a.lifecycleSnapshot(),b.lifecycleSnapshot())
        assertEquals(a.trace,b.trace)
    }
    @Test fun randomizedPublicLifecycleAndConfigurationSequences() {
        val random=Random(783127)
        for (zone in listOf("Etc/GMT","America/New_York","Pacific/Apia")) {
            val (a,b)=pair(zone)
            repeat(2400) {
                val operation=random.nextInt(20); val field=random.nextInt(-1,25); val value=random.nextInt(-2,35)
                val time=listOf(Long.MIN_VALUE,Grego.MIN_MILLIS,Grego.MIN_MILLIS-1,-1L,0L,1710055800000L,Grego.MAX_MILLIS,Grego.MAX_MILLIS+1,Long.MAX_VALUE).random(random)
                val code=listOf(null,"M01","M02","M12","M13","M01L","m01","").random(random)
                val option=random.nextInt(-1,4); val first=random.nextInt(-1,9); val minimal=random.nextInt(-2,10)
                val lenient=random.nextBoolean()
                compare(a,b) { p ->
                    when(operation) {
                        0 -> p.clearFields()
                        1 -> p.clearField(field)
                        2 -> p.setField(field,value)
                        3 -> p.getField(field)
                        4 -> p.fieldIsSet(field)
                        5 -> { p.timeInMillis=time; Unit }
                        6 -> p.timeInMillis
                        7 -> { p.isLenient=lenient; Unit }
                        8 -> { p.firstDayOfWeek=first; p.minimalDaysInFirstWeek=minimal; Unit }
                        9 -> { p.repeatedWallTimeOption=option; Unit }
                        10 -> { p.skippedWallTimeOption=option; Unit }
                        11 -> { p.setTemporalMonthCode(code); Unit }
                        12 -> p.temporalMonthCode
                        13 -> { p.setDateFields(2000+value,field,value); Unit }
                        14 -> { p.setDateTimeFields(2024,field,value,value,value,value); Unit }
                        15 -> { p.assignDate(if (option<0) null else Date(time)); Unit }
                        16 -> p.dateValue().getTime()
                        17 -> { p.setRelatedYear(value); Unit }
                        18 -> p.relatedYear
                        else -> { p.finish(); Unit }
                    }
                }
            }
        }
    }
    @Test fun byteStampCompressionTiesAndPartialFailures() {
        val (a,b)=pair(); compare(a,b) { it.clearFields() }
        repeat(900) { n -> compare(a,b) { it.setField(n%24,n) } }
        val random=Random(78127)
        repeat(200) {
            val stamps=ByteArray(24) { listOf(-128,-1,0,1,2,10,100,126,127).random(random).toByte() }
            for (p in listOf(a,b)) { p.clearFields(); stamps.copyInto(p.rawStamps()); p.forceLifecycle("nextStamp",127.toByte()) }
            compare(a,b) { p -> p.setField(12,123) }
        }
        for (field in listOf(-1,24,Int.MIN_VALUE,Int.MAX_VALUE)) {
            compare(a,b) { it.timeInMillis=0; Unit }
            compare(a,b) { it.setField(field,123) }
            compare(a,b) { it.fieldIsSet(field) }
            compare(a,b) { it.clearField(field) }
            compare(a,b) { it.getField(field) }
        }
    }
    @Test fun datesAliasesTemporalRelatedYearsAndClones() {
        val (a,b)=pair("America/New_York")
        for (month in 0..11) {
            compare(a,b) { it.clearFields(); it.setDateTimeFields(2024,month,1,12,34,56) }
            compare(a,b) { it.temporalMonthCode }
            compare(a,b) { it.inTemporalLeapYear() }
            compare(a,b) { it.dateValue().also { date -> date.time=0 }.getTime() }
            compare(a,b) { it.timeInMillis }
            compare(a,b) { p -> p.clone().apply { setField(1,2025) }.getField(1) }
        }
        for (field in listOf(2,23)) {
            compare(a,b) { it.setField(2,5); it.setField(23,7); it.clearField(field) }
        }
        val zone=TimeZone.getTimeZone("Europe/Paris")
        compare(a,b) { it.timeZone=zone; Unit }
        assertSame(zone,a.timeZone); assertSame(zone,b.timeZone)
        compare(a,b) { it.timeInMillis=0; Unit }
        compare(a,b) { it.timeZone=null; Unit }
        compare(a,b) { it.timeZone?.id }
        compare(a,b) { it.timeInMillis }
        compare(a,b) { it.getField(2) }
    }
    companion object {
        private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
            when(e) { is VirtualMachineError -> throw e; is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> e.javaClass.simpleName+":"+e.message }
        }
    }
}
