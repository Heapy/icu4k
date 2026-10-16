package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.impl.EraRules
import com.ibm.icu.util.TimeZone
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.util.UResourceBundle
import kotlin.test.*

class EraRulesCoreOracleTest {
    private class Host : EraRulesCore.Owner {
        override fun currentTimeMillis()=System.currentTimeMillis()
        override fun defaultZoneOffset(time: Long)=TimeZone.getDefault().getOffset(time)
    }
    private fun outcome(action: ()->Any?): Any?=try { action() } catch(e: VirtualMachineError) { throw e } catch(e: Exception) {
        (if(e is IndexOutOfBoundsException) "bounds" else e.javaClass.name.substringAfterLast('.')) to if(e is IndexOutOfBoundsException || e is NullPointerException) null else e.message
    }
    @Test fun everyCalendarEraAndFillArrayMatchesSource() {
        val ids=UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME,"supplementalData")["calendarData"].keySet()
        for(id in ids) for(tentative in listOf(false,true)) {
            var source: EraRules?=null; var target: EraRulesCore?=null
            assertEquals(outcome { source=EraRules.getInstance(id,tentative); Unit },outcome { target=EraRulesCore.getInstance(id,tentative,Host()); Unit },"$id/$tentative")
            if(source==null || target==null) continue
            val a=source!!; val b=target!!
            assertEquals(a.numberOfEras,b.getNumberOfEras(),id)
            assertEquals(a.maxEraCode,b.getMaxEraCode(),id)
            assertEquals(a.currentEraCode,b.getCurrentEraCode(),id)
            for(era in (-1..a.maxEraCode+1).toList()+listOf(Int.MIN_VALUE,Int.MAX_VALUE)) {
                assertEquals(outcome { a.getStartYear(era) },outcome { b.getStartYear(era) },"$id/$era")
                for(size in -1..4) {
                    val x=if(size<0) null else IntArray(size) { 123 }
                    val y=x?.copyOf()
                    var rx: IntArray?=null; var ry: IntArray?=null
                    assertEquals(outcome { rx=a.getStartDate(era,x); rx!!.toList() },outcome { ry=b.getStartDate(era,y); ry!!.toList() },"$id/$era/$size")
                    assertEquals(x?.toList(),y?.toList())
                    if(rx!=null && ry!=null) assertEquals(rx===x,ry===y)
                }
                val start=try { a.getStartDate(era,null) } catch(_: IllegalArgumentException) { continue }
                for(day in listOf(start[2]-1,start[2],start[2]+1)) {
                    assertEquals(outcome { a.getEraCode(start[0],start[1],day) },outcome { b.getEraCode(start[0],start[1],day) },"$id/$era/$day")
                }
            }
            for(year in listOf(Int.MIN_VALUE,-32769,-32768,-1,0,1,1970,2019,32767,32768,Int.MAX_VALUE)) for(month in listOf(0,1,12,13)) for(day in listOf(0,1,31,32)) {
                assertEquals(outcome { a.getEraCode(year,month,day) },outcome { b.getEraCode(year,month,day) },"$id/$year/$month/$day")
            }
        }
    }
}
