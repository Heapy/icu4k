package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.util.Calendar
import io.github.heapy.icu4k.util.Date
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.util.UResourceBundle
import kotlin.test.*

class CalendarWeekendCoreOracleTest {
    private fun outcome(action: () -> Any?): Any? = try { action() } catch(e: VirtualMachineError) { throw e } catch(e: Exception) {
        e.javaClass.name to if(e is NullPointerException || e is IndexOutOfBoundsException) null else e.message
    }
    @Test fun everyRegionValueHashFreshnessAndCache() {
        val regions=UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME,"supplementalData")["weekData"].keySet()+listOf("ZZ","us","","001",null)
        for(region in regions) {
            val source=Calendar.getWeekDataForRegion(region)
            val target=CalendarWeekDataCore.getWeekDataForRegion(region)
            assertEquals(source.toString(),target.toString(),region)
            assertEquals(source.hashCode(),target.hashCode(),region)
            assertEquals(source.toCommonWeekData(),target)
            assertNotSame(target,CalendarWeekDataCore.getWeekDataForRegion(region))
            assertSame(CalendarWeekDataCore.cachedForRegion(region),CalendarWeekDataCore.cachedForRegion(region))
        }
    }
    @Test fun customBoundariesPredicatesAndPartialConfigurationUpdates() {
        for(onset in 0..8) for(cease in 0..8) for(onsetTime in listOf(0,1,43200000,86400000,-1)) for(ceaseTime in listOf(0,43200000,86400000,86400001)) {
            val source=GregorianCalendarCoreOracleTest.Probe()
            val target=GregorianCalendarCoreOracleTest.Probe().apply { common=true }
            val value=Calendar.WeekData(2,4,onset,onsetTime,cease,ceaseTime)
            for(p in listOf(source,target)) { p.timeInMillis=0; assertSame(p,p.setWeekData(value)); p.trace.clear() }
            for(day in -1..9) {
                assertEquals(outcome { source.getDayOfWeekType(day) },outcome { target.getDayOfWeekType(day) })
                assertEquals(outcome { source.getWeekendTransition(day) },outcome { target.getWeekendTransition(day) })
            }
            for(date in listOf(0L,43200000L,86400000L,518400000L,561600000L)) {
                assertEquals(source.isWeekend(Date(date)),target.isWeekend(Date(date)),"$value/$date")
            }
            assertEquals(source.weekData.toString(),target.weekData.toString())
            assertEquals(source.rawFields().toList(),target.rawFields().toList())
            assertEquals(source.rawStamps().toList(),target.rawStamps().toList())
            assertEquals(source.lifecycleSnapshot(),target.lifecycleSnapshot())
            assertEquals(source.trace,target.trace)
            assertNotSame(target.weekData,target.weekData)
            val cloned=target.clone()
            assertEquals(source.weekData.toString(),cloned.weekData.toString())
            cloned.setWeekData(Calendar.WeekData(7,1,5,0,6,86400000))
            assertEquals(value.toString(),target.weekData.toString())
        }
        val source=GregorianCalendarCoreOracleTest.Probe()
        val target=GregorianCalendarCoreOracleTest.Probe().apply { common=true }
        for(p in listOf(source,target)) p.timeInMillis=0
        for(value in listOf(null,Calendar.WeekData(8,4,1,0,7,0),Calendar.WeekData(3,-100,9,-1,-1,Int.MAX_VALUE))) {
            assertEquals(outcome { source.setWeekData(value); true },outcome { target.setWeekData(value); true })
            assertEquals(source.weekData.toString(),target.weekData.toString())
            assertEquals(source.lifecycleSnapshot(),target.lifecycleSnapshot())
        }
        assertEquals(outcome { source.isWeekend(null) },outcome { target.isWeekend(null) })
    }
}
