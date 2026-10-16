package io.github.heapy.icu4k.impl.calendar

import kotlin.test.*

class CalendarAddCoreOracleTest {
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: VirtualMachineError) { throw e } catch (e: Exception) { e.javaClass.name to e.message }

    @Test fun arithmeticAcrossCutoverLeapDaysAndOffsetTransitions() {
        for (zone in listOf("Etc/GMT", "America/New_York", "Pacific/Apia")) {
            for (date in listOf(intArrayOf(2024,0,31,12,30,0), intArrayOf(2024,2,9,2,30,0), intArrayOf(2024,10,2,1,30,0), intArrayOf(2011,11,29,12,0,0), intArrayOf(1582,9,4,0,0,0), intArrayOf(0,1,29,5,0,0))) {
                for (field in -1..24) for (amount in listOf(-13,-1,0,1,13)) for (option in 0..2) {
                    val source = GregorianCalendarCoreOracleTest.Probe(zone)
                    val target = GregorianCalendarCoreOracleTest.Probe(zone).apply { common=true }
                    for (p in listOf(source,target)) {
                        p.clearFields(); p.setDateTimeFields(date[0],date[1],date[2],date[3],date[4],date[5])
                        p.skippedWallTimeOption=option
                        p.trace.clear()
                    }
                    val expected=outcome { source.add(field,amount) }
                    val actual=outcome { target.add(field,amount) }
                    val context="$zone ${date.toList()} field=$field amount=$amount option=$option"
                    assertEquals(expected,actual,context)
                    assertEquals(outcome { source.timeInMillis },outcome { target.timeInMillis },context)
                    assertEquals(source.rawFields().toList(),target.rawFields().toList(),context)
                    assertEquals(source.rawStamps().toList(),target.rawStamps().toList(),context)
                    assertEquals(source.lifecycleSnapshot(),target.lifecycleSnapshot(),context)
                    assertEquals(source.trace,target.trace,context)
                }
            }
        }
    }

    @Test fun actualLimitsAndPreparationAcrossWeekConfigurations() {
        for (first in 1..7) for (minimal in 1..7) for (month in listOf(0,1,8,11)) {
            val source=GregorianCalendarCoreOracleTest.Probe()
            val target=GregorianCalendarCoreOracleTest.Probe().apply { common=true }
            for (p in listOf(source,target)) { p.clearFields(); p.setDateFields(2024,month,29); p.firstDayOfWeek=first; p.minimalDaysInFirstWeek=minimal }
            for (field in 0..23) {
                assertEquals(source.getActualMaximum(field),target.getActualMaximum(field),"$first/$minimal/$month/$field")
                assertEquals(source.getActualMinimum(field),target.getActualMinimum(field))
                assertEquals(source.getMinimum(field),target.getMinimum(field))
                assertEquals(source.getMaximum(field),target.getMaximum(field))
                assertEquals(source.getLeastMaximum(field),target.getLeastMaximum(field))
                assertEquals(source.getGreatestMinimum(field),target.getGreatestMinimum(field))
            }
            assertEquals(source.rawFields().toList(),target.rawFields().toList())
            assertEquals(source.rawStamps().toList(),target.rawStamps().toList())
        }
    }
}
