package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.util.GregorianCalendar
import io.github.heapy.icu4k.util.Date
import kotlin.test.*

class CalendarComparisonCoreOracleTest {
    private fun outcome(action: () -> Any?): Any? = try { action() } catch(e: VirtualMachineError) { throw e } catch(e: Exception) {
        e.javaClass.name to if(e is NullPointerException || e is IndexOutOfBoundsException) null else e.message
    }

    @Test fun differenceSearchWithLeapDaysCutoverAndTransitions() {
        for(zone in listOf("Etc/GMT","America/New_York","Pacific/Apia")) {
            for(start in listOf(-12219292800000L,951782400000L,1325152800000L,1710052200000L,1730611800000L)) {
                for(delta in listOf(-31622400001L,-2678400000L,-90000000L,-1L,0L,1L,90000000L,2678400000L,126230400001L)) {
                    for(field in listOf(-1,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,17,18,19,20,21,22,23,24)) {
                        val source=GregorianCalendarCoreOracleTest.Probe(zone).apply { timeInMillis=start; trace.clear() }
                        val target=GregorianCalendarCoreOracleTest.Probe(zone).apply { common=true; timeInMillis=start; trace.clear() }
                        val context="$zone/$start/$delta/$field"
                        assertEquals(outcome { source.fieldDifference(Date(start+delta),field) },outcome { target.fieldDifference(Date(start+delta),field) },context)
                        assertEquals(source.rawTime(),target.rawTime(),context)
                        assertEquals(source.rawFields().toList(),target.rawFields().toList(),context)
                        assertEquals(source.rawStamps().toList(),target.rawStamps().toList(),context)
                        assertEquals(source.lifecycleSnapshot(),target.lifecycleSnapshot(),context)
                        assertEquals(source.trace,target.trace,context)
                    }
                }
            }
        }
    }

    @Test fun comparisonsConfigurationAndGregorianCutover() {
        for(mode in 0..10) {
            val source=GregorianCalendarCoreOracleTest.Probe()
            val target=GregorianCalendarCoreOracleTest.Probe().apply { common=true }
            val sourceOther=GregorianCalendarCoreOracleTest.Probe()
            val targetOther=GregorianCalendarCoreOracleTest.Probe().apply { common=true }
            for(p in listOf(source,target,sourceOther,targetOther)) { p.clearFields(); p.setDateFields(2024,1,29) }
            for(p in listOf(sourceOther,targetOther)) when(mode) {
                1 -> p.setField(5,28)
                2 -> p.isLenient=false
                3 -> p.firstDayOfWeek=3
                4 -> p.minimalDaysInFirstWeek=7
                5 -> p.repeatedWallTimeOption=1
                6 -> p.skippedWallTimeOption=2
                7 -> p.timeZone=com.ibm.icu.util.TimeZone.getTimeZone("America/New_York")
                8 -> p.change(Date(0))
                9 -> p.timeZone=null
                10 -> { p.isLenient=false; p.setField(2,99) }
            }
            assertEquals(outcome { source.hashCode() },outcome { target.hashCode() })
            assertEquals(outcome { sourceOther.hashCode() },outcome { targetOther.hashCode() })
            for(operation in 0..4) {
                fun run(p: GregorianCalendarCoreOracleTest.Probe,other: GregorianCalendarCoreOracleTest.Probe): Any = when(operation) {
                    0 -> p.equals(other)
                    1 -> p.isEquivalentTo(other)
                    2 -> p.compareTo(other)
                    3 -> p.before(other)
                    else -> p.after(other)
                }
                assertEquals(outcome { run(source,sourceOther) },outcome { run(target,targetOther) },"$mode/$operation")
                assertEquals(source.lifecycleSnapshot(),target.lifecycleSnapshot())
                assertEquals(sourceOther.lifecycleSnapshot(),targetOther.lifecycleSnapshot())
            }
            for(arg in listOf(null,"bad",42,Date(Long.MIN_VALUE),Date(Long.MAX_VALUE),GregorianCalendar())) {
                assertEquals(outcome { source.equals(arg) },outcome { target.equals(arg) })
                assertEquals(outcome { source.before(arg) },outcome { target.before(arg) })
                assertEquals(outcome { source.after(arg) },outcome { target.after(arg) })
            }
            assertTrue(target.equals(target))
            assertEquals(outcome { source.compareTo(null) },outcome { target.compareTo(null) })
            assertEquals(outcome { source.isEquivalentTo(null) },outcome { target.isEquivalentTo(null) })
            assertEquals(outcome { source.fieldDifference(null,5) },outcome { target.fieldDifference(null,5) })
        }
    }
}
