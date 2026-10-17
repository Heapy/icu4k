@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import com.ibm.icu.text.SimpleDateFormat as Source
import com.ibm.icu.util.ULocale as SourceLocale
import com.ibm.icu.util.TimeZone as SourceZone
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class SimpleDateFormatIntervalCoreOracleTest {
    private fun outcome(block:()->Any?):Any?=try { block() } catch(e:Throwable) {
        if(e is VirtualMachineError) throw e
        when(e) { is NullPointerException->"null"; is IndexOutOfBoundsException->"bounds"; else->e.javaClass.simpleName+":"+e.message }
    }
    @Test fun intervalRangesFieldsAndApostrophesAcrossCalendars() {
        for(id in listOf("en_US","ru","ar_EG","ja@calendar=japanese","en@calendar=hebrew","zh@calendar=chinese")) for(fast in listOf(true,false)) {
            val a=if(fast) Source("",SourceLocale(id)) else Source("",null as String?,SourceLocale(id))
            val b=SimpleDateFormatStateFixture(id,"",null,fast)
            a.timeZone=SourceZone.getTimeZone("Etc/UTC"); b.setTimeZone(TimeZone.getTimeZone("Etc/UTC"))
            for(pattern in listOf("MMM d, y HH:mm","y-M-d","HH:mm:ss","Q yyyy","EEEE, MMMM d","B h:mm","G y年M月d日","'prefix 'yyyy 'x' MM ''dd","j","yyyy N MM","","'literal'","SSSS xxxxx")) {
                a.applyPattern(pattern); b.state.applyPattern(pattern)
                for((start,end) in listOf(0L to 0L,0L to 60_000L,0L to 86_400_000L,0L to 31_536_000_000L,1556668800000L to 1556755200000L)) {
                    val ac=a.calendar.clone(); val at=ac.clone(); ac.timeInMillis=start; at.timeInMillis=end
                    val bc=b.getCalendar()!!.clone(); val bt=bc.clone(); bc.setTimeInMillis(start); bt.setTimeInMillis(end)
                    for(field in listOf(-1,DateFormat.YEAR_FIELD,DateFormat.MINUTE_FIELD)) {
                        val ap=java.text.FieldPosition(field).also { it.beginIndex=4; it.endIndex=8 }
                        val bp=FormatFieldPosition(field).also { it.setBeginIndex(4); it.setEndIndex(8) }
                        val ab=StringBuffer("prefix:"); val bb=StringBuilder("prefix:")
                        val label="$id/$fast/$pattern/$start/$end/$field"
                        assertEquals(outcome { a.intervalFormatByAlgorithm(ac,at,ab,ap).toString() },outcome { b.interval.format(bc,bt,bb,bp).toString() },label)
                        assertEquals(ab.toString(),bb.toString(),label)
                        assertEquals(listOf(ap.beginIndex,ap.endIndex),listOf(bp.getBeginIndex(),bp.getEndIndex()),label)
                    }
                }
            }
        }
    }
}
