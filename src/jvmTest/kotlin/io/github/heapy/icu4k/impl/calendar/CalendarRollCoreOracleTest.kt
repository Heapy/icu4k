package io.github.heapy.icu4k.impl.calendar

import kotlin.test.*

class CalendarRollCoreOracleTest {
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: VirtualMachineError) { throw e } catch (e: Exception) { e.javaClass.name to e.message }

    @Test fun baseAndGregorianRollAcrossBoundariesAndTransitions() {
        for (zone in listOf("Etc/GMT","America/New_York","Pacific/Apia")) {
            for (date in listOf(intArrayOf(2024,0,31,12,30,0),intArrayOf(2024,2,10,1,30,0),intArrayOf(2024,10,3,1,30,0),intArrayOf(2011,11,29,12,0,0),intArrayOf(1582,9,4,0,0,0),intArrayOf(0,1,29,5,0,0))) {
                for (field in -1..24) for (amount in listOf(Int.MIN_VALUE,-53,-1,0,1,53,Int.MAX_VALUE)) for (base in listOf(false,true)) {
                    val source=GregorianCalendarCoreOracleTest.Probe(zone)
                    val target=GregorianCalendarCoreOracleTest.Probe(zone).apply { common=true }
                    for(p in listOf(source,target)) {
                        p.clearFields(); p.setDateTimeFields(date[0],date[1],date[2],date[3],date[4],date[5]); p.trace.clear()
                    }
                    fun roll(p: GregorianCalendarCoreOracleTest.Probe) { if(base) p.baseRoll(field,amount) else p.roll(field,amount) }
                    val context="$zone ${date.toList()} field=$field amount=$amount base=$base"
                    assertEquals(outcome { roll(source) },outcome { roll(target) },context)
                    assertEquals(source.rawTime(),target.rawTime(),context)
                    assertEquals(source.rawFields().toList(),target.rawFields().toList(),context)
                    assertEquals(source.rawStamps().toList(),target.rawStamps().toList(),context)
                    assertEquals(source.lifecycleSnapshot(),target.lifecycleSnapshot(),context)
                    assertEquals(source.trace,target.trace,context)
                    assertEquals(outcome { source.timeInMillis },outcome { target.timeInMillis },context)
                }
            }
        }
    }

    @Test fun weekRollWithEveryWeekConfiguration() {
        for (first in 1..7) for (minimal in 1..7) for (field in listOf(3,4)) for (amount in listOf(-53,-1,1,53)) for (day in listOf(1,29,31)) {
            val source=GregorianCalendarCoreOracleTest.Probe()
            val target=GregorianCalendarCoreOracleTest.Probe().apply { common=true }
            for(p in listOf(source,target)) { p.clearFields(); p.setDateFields(2024,11,day); p.firstDayOfWeek=first; p.minimalDaysInFirstWeek=minimal; p.trace.clear() }
            source.baseRoll(field,amount); target.baseRoll(field,amount)
            assertEquals(source.rawFields().toList(),target.rawFields().toList(),"$first/$minimal/$field/$amount/$day")
            assertEquals(source.rawStamps().toList(),target.rawStamps().toList())
            assertEquals(source.trace,target.trace)
            assertEquals(source.timeInMillis,target.timeInMillis)
        }
    }
}
